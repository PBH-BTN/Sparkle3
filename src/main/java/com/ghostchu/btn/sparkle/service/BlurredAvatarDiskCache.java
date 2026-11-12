package com.ghostchu.btn.sparkle.service;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;

@Service
@Slf4j
public class BlurredAvatarDiskCache {
    private final Path directory;
    private final Duration ttl;

    public BlurredAvatarDiskCache(
            @Value("${sparkle.user-avatar.cache-directory:data/avatar-cache}") String directory,
            @Value("${sparkle.user-avatar.cache-duration:2592000000}") long ttlMillis
    ) throws IOException {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.ttl = Duration.ofMillis(ttlMillis);
        Files.createDirectories(this.directory);
    }

    public @Nullable byte[] get(long userId, @Nullable String avatarUrl) throws IOException {
        Path file = cacheFile(userId, avatarUrl);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
        if (age >= ttl.toMillis()) {
            Files.deleteIfExists(file);
            return null;
        }
        return Files.readAllBytes(file);
    }

    public void put(long userId, @Nullable String avatarUrl, byte[] data) throws IOException {
        Files.createDirectories(directory);
        Path target = cacheFile(userId, avatarUrl);
        Path temporary = Files.createTempFile(directory, userId + "-", ".tmp");
        try {
            Files.write(temporary, data);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            removeUserFilesExcept(userId, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void refreshOnLogin(long userId) {
        removeUserFiles(userId);
    }

    @Scheduled(cron = "${sparkle.user-avatar.cleanup-cron:0 30 4 * * *}")
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - ttl.toMillis();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.jpg")) {
            for (Path file : files) {
                try {
                    FileTime modified = Files.getLastModifiedTime(file);
                    if (modified.toMillis() < cutoff) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException exception) {
                    log.debug("Unable to clean avatar cache file {}: {}",
                            file, exception.getMessage());
                }
            }
        } catch (IOException exception) {
            log.warn("Unable to scan avatar cache directory {}: {}",
                    directory, exception.getMessage());
        }
    }

    Path cacheFile(long userId, @Nullable String avatarUrl) {
        String sourceHash = Hashing.sha256()
                .hashString(avatarUrl == null ? "" : avatarUrl, StandardCharsets.UTF_8)
                .toString();
        return directory.resolve(userId + "-" + sourceHash + ".jpg").normalize();
    }

    private void removeUserFilesExcept(long userId, Path retained) {
        try (DirectoryStream<Path> files =
                     Files.newDirectoryStream(directory, userId + "-*.jpg")) {
            for (Path file : files) {
                if (!file.equals(retained)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException exception) {
            log.debug("Unable to remove stale avatar cache files for user {}: {}",
                    userId, exception.getMessage());
        }
    }

    private void removeUserFiles(long userId) {
        try (DirectoryStream<Path> files =
                     Files.newDirectoryStream(directory, userId + "-*.jpg")) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException exception) {
            log.warn("Unable to invalidate avatar cache for user {}: {}",
                    userId, exception.getMessage());
        }
    }
}
