package com.ghostchu.btn.sparkle.service;

import com.google.common.hash.Hashing;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
public class BlurredAvatarDiskCache {
    private static final String CONFIG_DIRECTORY = "sparkle.user-avatar.cache-directory";
    private static final String CONFIG_DURATION = "sparkle.user-avatar.cache-duration";
    private static final String CONFIG_CLEANUP_CRON = "sparkle.user-avatar.cleanup-cron";
    private static final Duration CONFIG_CACHE_TTL = Duration.ofSeconds(5);

    private final DatabaseConfigService databaseConfigService;
    private final TaskScheduler taskScheduler;
    private final String defaultDirectory;
    private final long defaultTtlMillis;
    private final String defaultCleanupCron;
    private volatile RuntimeConfig runtimeConfig;
    private volatile long runtimeConfigExpiresAt;
    private volatile ScheduledFuture<?> cleanupFuture;
    private volatile String scheduledCron;

    public BlurredAvatarDiskCache(
            DatabaseConfigService databaseConfigService,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler,
            @Value("${sparkle.user-avatar.cache-directory:data/avatar-cache}") String directory,
            @Value("${sparkle.user-avatar.cache-duration:2592000000}") long ttlMillis,
            @Value("${sparkle.user-avatar.cleanup-cron:0 30 4 * * *}") String cleanupCron
    ) throws IOException {
        this.databaseConfigService = databaseConfigService;
        this.taskScheduler = taskScheduler;
        this.defaultDirectory = directory;
        this.defaultTtlMillis = ttlMillis;
        this.defaultCleanupCron = cleanupCron;
        this.runtimeConfig = new RuntimeConfig(
                normalizedDirectory(directory),
                Duration.ofMillis(ttlMillis),
                cleanupCron
        );
        Files.createDirectories(runtimeConfig.directory());
    }

    public @Nullable byte[] get(long userId, @Nullable String avatarUrl) throws IOException {
        RuntimeConfig config = runtimeConfig();
        Path file = cacheFile(userId, avatarUrl, config.directory());
        if (!Files.isRegularFile(file)) {
            return null;
        }
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
        if (age >= config.ttl().toMillis()) {
            Files.deleteIfExists(file);
            return null;
        }
        return Files.readAllBytes(file);
    }

    public void put(long userId, @Nullable String avatarUrl, byte[] data) throws IOException {
        RuntimeConfig config = runtimeConfig();
        Path directory = config.directory();
        Files.createDirectories(directory);
        Path target = cacheFile(userId, avatarUrl, directory);
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
        removeUserFiles(userId, runtimeConfig().directory());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCleanupSchedule() {
        refreshCleanupSchedule();
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void pollCleanupSchedule() {
        refreshCleanupSchedule();
    }

    public void cleanupExpired() {
        RuntimeConfig config = runtimeConfig();
        Path directory = config.directory();
        long cutoff = System.currentTimeMillis() - config.ttl().toMillis();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            log.warn("Unable to create avatar cache directory {}: {}",
                    directory, exception.getMessage());
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                try {
                    FileTime modified = Files.getLastModifiedTime(file);
                    String fileName = file.getFileName().toString();
                    if ((fileName.endsWith(".jpg") || fileName.endsWith(".tmp"))
                            && modified.toMillis() < cutoff) {
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
        return cacheFile(userId, avatarUrl, runtimeConfig().directory());
    }

    private Path cacheFile(long userId, @Nullable String avatarUrl, Path directory) {
        String sourceHash = Hashing.sha256()
                .hashString(avatarUrl == null ? "" : avatarUrl, StandardCharsets.UTF_8)
                .toString();
        return directory.resolve(userId + "-" + sourceHash + ".jpg").normalize();
    }

    private void removeUserFilesExcept(long userId, Path retained) {
        Path directory = retained.getParent();
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

    private void removeUserFiles(long userId, Path directory) {
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

    private synchronized void refreshCleanupSchedule() {
        RuntimeConfig config = runtimeConfig();
        String cron = config.cleanupCron();
        if (cron == null || cron.isBlank() || "-".equals(cron)) {
            cancelCleanupSchedule();
            return;
        }
        if (Objects.equals(cron, scheduledCron)
                && cleanupFuture != null
                && !cleanupFuture.isCancelled()
                && !cleanupFuture.isDone()) {
            return;
        }
        try {
            CronExpression.parse(cron);
            cancelCleanupSchedule();
            cleanupFuture = taskScheduler.schedule(this::cleanupExpired, new CronTrigger(cron));
            scheduledCron = cron;
            log.info("Scheduled blurred avatar cache cleanup with cron '{}'", cron);
        } catch (IllegalArgumentException exception) {
            cancelCleanupSchedule();
            log.error("Invalid avatar cache cleanup cron '{}'", cron, exception);
        }
    }

    private void cancelCleanupSchedule() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
        }
        scheduledCron = null;
    }

    private RuntimeConfig runtimeConfig() {
        long now = System.currentTimeMillis();
        RuntimeConfig cached = runtimeConfig;
        if (cached != null && now < runtimeConfigExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (runtimeConfig != null && now < runtimeConfigExpiresAt) {
                return runtimeConfig;
            }
            Map<String, String> values;
            try {
                values = databaseConfigService.getRawValues(List.of(
                        CONFIG_DIRECTORY,
                        CONFIG_DURATION,
                        CONFIG_CLEANUP_CRON
                ));
            } catch (Exception exception) {
                log.warn("Unable to load avatar cache configuration: {}",
                        exception.getMessage());
                values = Map.of();
            }
            String directory = values.getOrDefault(CONFIG_DIRECTORY, defaultDirectory);
            long ttlMillis = parsePositiveLong(
                    values.get(CONFIG_DURATION),
                    defaultTtlMillis
            );
            String cleanupCron = values.getOrDefault(CONFIG_CLEANUP_CRON, defaultCleanupCron);
            runtimeConfig = new RuntimeConfig(
                    normalizedDirectory(directory),
                    Duration.ofMillis(ttlMillis),
                    cleanupCron
            );
            runtimeConfigExpiresAt = now + CONFIG_CACHE_TTL.toMillis();
            return runtimeConfig;
        }
    }

    private Path normalizedDirectory(String directory) {
        String resolved = directory == null || directory.isBlank()
                ? defaultDirectory
                : directory;
        return Path.of(resolved).toAbsolutePath().normalize();
    }

    private long parsePositiveLong(String value, long fallback) {
        try {
            long parsed = value == null ? fallback : Long.parseLong(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @PreDestroy
    public void close() {
        cancelCleanupSchedule();
    }

    private record RuntimeConfig(Path directory, Duration ttl, String cleanupCron) {
    }
}
