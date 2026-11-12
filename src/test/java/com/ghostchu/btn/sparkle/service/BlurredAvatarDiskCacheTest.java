package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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

    @Test
    void registersCleanupScheduleFromDatabaseConfiguration() throws Exception {
        DatabaseConfigService configService = mock(DatabaseConfigService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(configService.getRawValues(anyList())).thenReturn(Map.of(
                "sparkle.user-avatar.cache-directory", temporaryDirectory.toString(),
                "sparkle.user-avatar.cache-duration", "60000",
                "sparkle.user-avatar.cleanup-cron", "0 30 4 * * *"
        ));
        doReturn(future)
                .when(scheduler)
                .schedule(any(Runnable.class), any(CronTrigger.class));

        BlurredAvatarDiskCache cache = new BlurredAvatarDiskCache(
                configService,
                scheduler,
                temporaryDirectory.toString(),
                60_000,
                "0 30 4 * * *"
        );
        cache.initializeCleanupSchedule();

        verify(scheduler).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    private BlurredAvatarDiskCache cache(long ttlMillis) throws Exception {
        DatabaseConfigService configService = mock(DatabaseConfigService.class);
        when(configService.getRawValues(anyList())).thenReturn(Map.of(
                "sparkle.user-avatar.cache-directory", temporaryDirectory.toString(),
                "sparkle.user-avatar.cache-duration", Long.toString(ttlMillis),
                "sparkle.user-avatar.cleanup-cron", "-"
        ));
        return new BlurredAvatarDiskCache(
                configService,
                mock(TaskScheduler.class),
                temporaryDirectory.toString(),
                ttlMillis,
                "-"
        );
    }
}
