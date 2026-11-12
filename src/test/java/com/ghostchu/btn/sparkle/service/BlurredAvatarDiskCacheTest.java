package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlurredAvatarDiskCacheTest {
    private static final String AVATAR_URL = "https://example.com/avatar-a.jpg";
    private static final String NEW_AVATAR_URL = "https://example.com/avatar-b.jpg";

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndReadsImagesFromDisk() throws Exception {
        BlurredAvatarDiskCache cache = cache(60_000);
        byte[] image = {1, 2, 3, 4};

        cache.put(42L, AVATAR_URL, image);

        assertArrayEquals(image, cache.get(42L, AVATAR_URL));
    }

    @Test
    void changingAvatarUrlInvalidatesOldUserFile() throws Exception {
        BlurredAvatarDiskCache cache = cache(60_000);
        cache.put(42L, AVATAR_URL, new byte[]{1});
        cache.put(42L, NEW_AVATAR_URL, new byte[]{2});

        assertNull(cache.get(42L, AVATAR_URL));
        assertArrayEquals(new byte[]{2}, cache.get(42L, NEW_AVATAR_URL));
    }

    @Test
    void loginRefreshRemovesCachedFiles() throws Exception {
        BlurredAvatarDiskCache cache = cache(60_000);
        cache.put(42L, AVATAR_URL, new byte[]{1});

        cache.refreshOnLogin(42L);

        assertNull(cache.get(42L, AVATAR_URL));
    }

    @Test
    void expiredFilesAreDeleted() throws Exception {
        BlurredAvatarDiskCache cache = cache(1_000);
        cache.put(42L, AVATAR_URL, new byte[]{1});
        Path file = cache.cacheFile(42L, AVATAR_URL);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 2_000));

        assertNull(cache.get(42L, AVATAR_URL));
        assertFalse(Files.exists(file));
    }

    private BlurredAvatarDiskCache cache(long ttlMillis) throws Exception {
        return new BlurredAvatarDiskCache(temporaryDirectory.toString(), ttlMillis);
    }
}
