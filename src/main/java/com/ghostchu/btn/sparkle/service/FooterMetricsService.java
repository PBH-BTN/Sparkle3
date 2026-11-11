package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.SystemSample;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterPerformanceView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.HttpResponsePerformanceView;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

@Service
public class FooterMetricsService {
    private static final int WINDOW_SECONDS = 60;
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(10);
    private static final Duration SAMPLE_RETENTION = Duration.ofSeconds(70);
    private static final Object SAMPLE_LOCK = new Object();

    private final AdminMetricsService adminMetricsService;
    private final RedisTemplate<String, String> redisTemplate;
    private final Map<HttpCategory, Timer> responseTimers = new EnumMap<>(HttpCategory.class);
    private final Map<HttpCategory, RollingLatencyWindow> responseWindows =
            new EnumMap<>(HttpCategory.class);
    private final RollingLatencyWindow sqlWindow = new RollingLatencyWindow();
    private final Deque<MetricSample> samples = new ArrayDeque<>();
    private volatile long lastSampleAt;
    private Long previousDiskIoBytes;
    private Long previousNetworkBytes;

    public FooterMetricsService(AdminMetricsService adminMetricsService,
                                @Qualifier("stringStringRedisTemplate")
                                RedisTemplate<String, String> redisTemplate,
                                MeterRegistry meterRegistry) {
        this.adminMetricsService = adminMetricsService;
        this.redisTemplate = redisTemplate;
        for (HttpCategory category : HttpCategory.values()) {
            responseWindows.put(category, new RollingLatencyWindow());
            responseTimers.put(category, Timer.builder("sparkle.footer.http.response")
                    .tag("category", category.metricKey)
                    .publishPercentiles(0.99)
                    .distributionStatisticExpiry(Duration.ofMinutes(1))
                    .distributionStatisticBufferLength(2)
                    .register(meterRegistry));
        }
    }

    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void sample() {
        synchronized (SAMPLE_LOCK) {
            try {
                long now = System.currentTimeMillis();
                SystemSample system = adminMetricsService.currentSample();
                PlatformIo platformIo = platformIo();
                Long diskIoRate = rate(previousDiskIoBytes, platformIo.diskIoBytes(), now);
                Long networkRate = rate(previousNetworkBytes, platformIo.networkBytes(), now);
                previousDiskIoBytes = platformIo.diskIoBytes();
                previousNetworkBytes = platformIo.networkBytes();

                double loadAverage = ManagementFactory.getOperatingSystemMXBean()
                        .getSystemLoadAverage();
                samples.addLast(new MetricSample(
                        now,
                        system.cpuUsage() * 100,
                        system.memoryUsage() * 100,
                        loadAverage < 0 || !Double.isFinite(loadAverage) ? null : loadAverage,
                        platformIo.systemUptimeSeconds(),
                        ManagementFactory.getRuntimeMXBean().getUptime() / 1_000,
                        platformIo.diskIoRate() != null
                                ? platformIo.diskIoRate()
                                : diskIoRate == null ? null : diskIoRate.doubleValue(),
                        platformIo.networkRate() != null
                                ? platformIo.networkRate()
                                : networkRate == null ? null : networkRate.doubleValue(),
                        redisOpsPerSecond()
                ));
                long cutoff = now - SAMPLE_RETENTION.toMillis();
                while (samples.size() > 2 && samples.getFirst().timestamp() < cutoff) {
                    samples.removeFirst();
                }
                lastSampleAt = now;
            } catch (Exception ignored) {
                // A single unavailable platform metric must not break public footer rendering.
            }
        }
    }

    public void recordHttpRequest(@NotNull String requestUri, long durationNanos) {
        HttpCategory category = HttpCategory.classify(requestUri);
        responseWindows.get(category).record(durationNanos);
        responseTimers.get(category).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordSqlQuery() {
        sqlWindow.record(0);
    }

    public @NotNull FooterPerformanceView snapshot() {
        long now = System.currentTimeMillis();
        if (now - lastSampleAt >= SAMPLE_INTERVAL.toMillis()) {
            sample();
        }

        List<MetricSample> currentSamples;
        synchronized (SAMPLE_LOCK) {
            long cutoff = now - Duration.ofMinutes(1).toMillis();
            currentSamples = samples.stream()
                    .filter(sample -> sample.timestamp() >= cutoff)
                    .toList();
        }
        MetricSample latest = currentSamples.isEmpty() ? null : currentSamples.getLast();
        long requestWindowSeconds = requestWindowSeconds();
        long httpRequests = responseWindows.values().stream()
                .mapToLong(window -> window.stats().count())
                .sum();
        long sqlQueries = sqlWindow.stats().count();

        return new FooterPerformanceView(
                latest == null ? null : latest.loadAverage(),
                average(currentSamples, MetricSample::cpuPercent),
                average(currentSamples, MetricSample::memoryPercent),
                latest == null ? null : latest.systemUptimeSeconds(),
                latest == null ? null : latest.serverUptimeSeconds(),
                averageNullable(currentSamples, MetricSample::diskIoBytesPerSecond),
                averageNullable(currentSamples, MetricSample::networkBytesPerSecond),
                httpRequests / (double) requestWindowSeconds,
                sqlQueries / (double) requestWindowSeconds,
                averageNullable(currentSamples, MetricSample::redisOpsPerSecond),
                new HttpResponsePerformanceView(
                        averageMillis(HttpCategory.API),
                        p99Millis(HttpCategory.API),
                        averageMillis(HttpCategory.BTN_PING),
                        p99Millis(HttpCategory.BTN_PING),
                        averageMillis(HttpCategory.OTHER),
                        p99Millis(HttpCategory.OTHER)
                )
        );
    }

    private long requestWindowSeconds() {
        long nowSecond = System.currentTimeMillis() / 1_000;
        long elapsed = nowSecond - responseWindows.get(HttpCategory.API).startSecond() + 1;
        return Math.max(1, Math.min(WINDOW_SECONDS, elapsed));
    }

    private Double averageMillis(HttpCategory category) {
        RollingLatencyWindow.Stats stats = responseWindows.get(category).stats();
        return stats.count() == 0
                ? null
                : stats.totalNanos() / (double) stats.count() / 1_000_000;
    }

    private Double p99Millis(HttpCategory category) {
        HistogramSnapshot snapshot = responseTimers.get(category).takeSnapshot();
        for (ValueAtPercentile percentile : snapshot.percentileValues()) {
            if (Math.abs(percentile.percentile() - 0.99) < 0.000_001) {
                double value = percentile.value(TimeUnit.MILLISECONDS);
                return Double.isFinite(value) ? value : null;
            }
        }
        return null;
    }

    private Double average(List<MetricSample> samples, ToDoubleFunction<MetricSample> mapper) {
        return samples.isEmpty()
                ? null
                : samples.stream().mapToDouble(mapper).average().orElse(Double.NaN);
    }

    private Double averageNullable(List<MetricSample> samples,
                                   java.util.function.Function<MetricSample, Double> mapper) {
        return samples.stream()
                .map(mapper)
                .filter(value -> value != null && Double.isFinite(value))
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private @Nullable Long rate(@Nullable Long previous, @Nullable Long current, long now) {
        if (previous == null || current == null || current < previous || lastSampleAt <= 0) {
            return null;
        }
        double elapsedSeconds = Math.max(100, now - lastSampleAt) / 1_000.0;
        return Math.round((current - previous) / elapsedSeconds);
    }

    private @Nullable Double redisOpsPerSecond() {
        try {
            Properties info = redisTemplate.execute(
                    (RedisCallback<Properties>) connection ->
                            connection.serverCommands().info("stats")
            );
            if (info == null) return null;
            String value = info.getProperty("instantaneous_ops_per_sec");
            return value == null ? null : Double.parseDouble(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private PlatformIo platformIo() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return windowsPlatformIo();
        }
        return linuxPlatformIo();
    }

    private PlatformIo linuxPlatformIo() {
        Long uptime = null;
        Long diskIoBytes = null;
        Long networkBytes = null;
        try {
            String uptimeText = Files.readString(Path.of("/proc/uptime"), StandardCharsets.US_ASCII);
            uptime = Math.round(Double.parseDouble(uptimeText.trim().split("\\s+")[0]));
        } catch (Exception ignored) {
        }
        diskIoBytes = linuxDiskIoBytes();
        networkBytes = linuxNetworkBytes();
        return new PlatformIo(uptime, diskIoBytes, networkBytes, null, null);
    }

    private @Nullable Long linuxDiskIoBytes() {
        Path blockDirectory = Path.of("/sys/block");
        if (!Files.isDirectory(blockDirectory)) return null;
        long total = 0;
        boolean found = false;
        try (var devices = Files.list(blockDirectory)) {
            for (Path device : devices.toList()) {
                String name = device.getFileName().toString();
                if (name.startsWith("loop") || name.startsWith("ram")
                        || name.startsWith("dm-") || name.startsWith("md")) {
                    continue;
                }
                Path statFile = device.resolve("stat");
                if (!Files.isReadable(statFile)) continue;
                String[] fields = Files.readString(statFile, StandardCharsets.US_ASCII)
                        .trim()
                        .split("\\s+");
                if (fields.length < 7) continue;
                total += Long.parseLong(fields[2]) * 512L;
                total += Long.parseLong(fields[6]) * 512L;
                found = true;
            }
        } catch (Exception exception) {
            return null;
        }
        return found ? total : null;
    }

    private @Nullable Long linuxNetworkBytes() {
        try {
            long total = 0;
            boolean found = false;
            for (String line : Files.readAllLines(Path.of("/proc/net/dev"))) {
                int separator = line.indexOf(':');
                if (separator < 0) continue;
                String name = line.substring(0, separator).trim();
                if (name.equals("lo")) continue;
                String[] fields = line.substring(separator + 1).trim().split("\\s+");
                if (fields.length < 16) continue;
                total += Long.parseLong(fields[0]);
                total += Long.parseLong(fields[8]);
                found = true;
            }
            return found ? total : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private PlatformIo windowsPlatformIo() {
        String command = """
                $ErrorActionPreference = 'SilentlyContinue';
                $os = Get-CimInstance Win32_OperatingSystem;
                $uptime = [long]((Get-Date).ToUniversalTime() - $os.LastBootUpTime.ToUniversalTime()).TotalSeconds;
                $disk = [double](Get-CimInstance Win32_PerfFormattedData_PerfDisk_PhysicalDisk |
                    Where-Object { $_.Name -eq '_Total' } |
                    Select-Object -First 1 -ExpandProperty DiskBytesPersec);
                $net = [double]((Get-CimInstance Win32_PerfFormattedData_Tcpip_NetworkInterface |
                    Measure-Object -Property BytesTotalPersec -Sum).Sum);
                Write-Output ("{0}|{1}|{2}" -f $uptime, $disk, $net)
                """;
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle",
                    "Hidden",
                    "-Command",
                    command
            ).redirectErrorStream(false).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return PlatformIo.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .trim();
            String[] fields = output.split("\\|", -1);
            if (fields.length != 3) return PlatformIo.empty();
            Long uptime = parseNullableLong(fields[0]);
            Double disk = parseNullableDouble(fields[1]);
            Double network = parseNullableDouble(fields[2]);
            return new PlatformIo(uptime, null, null, disk, network);
        } catch (Exception exception) {
            return PlatformIo.empty();
        }
    }

    private Long parseNullableLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private Double parseNullableDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (Exception exception) {
            return null;
        }
    }

    public enum HttpCategory {
        API("api"),
        BTN_PING("btnPing"),
        OTHER("other");

        private final String metricKey;

        HttpCategory(String metricKey) {
            this.metricKey = metricKey;
        }

        public static HttpCategory classify(@NotNull String requestUri) {
            if (requestUri.startsWith("/api/")) return API;
            if (requestUri.startsWith("/ping/")) return BTN_PING;
            return OTHER;
        }
    }

    private record MetricSample(
            long timestamp,
            double cpuPercent,
            double memoryPercent,
            Double loadAverage,
            Long systemUptimeSeconds,
            Long serverUptimeSeconds,
            Double diskIoBytesPerSecond,
            Double networkBytesPerSecond,
            Double redisOpsPerSecond
    ) {
    }

    private record PlatformIo(
            Long systemUptimeSeconds,
            Long diskIoBytes,
            Long networkBytes,
            Double diskIoRate,
            Double networkRate
    ) {
        private static PlatformIo empty() {
            return new PlatformIo(null, null, null, null, null);
        }
    }

    private static final class RollingLatencyWindow {
        private final Slot[] slots = new Slot[WINDOW_SECONDS];
        private final long startSecond = System.currentTimeMillis() / 1_000;

        private RollingLatencyWindow() {
            for (int index = 0; index < slots.length; index++) {
                slots[index] = new Slot();
            }
        }

        private void record(long durationNanos) {
            long second = System.currentTimeMillis() / 1_000;
            Slot slot = slots[(int) Math.floorMod(second, WINDOW_SECONDS)];
            synchronized (slot) {
                if (slot.second != second) {
                    slot.second = second;
                    slot.count = 0;
                    slot.totalNanos = 0;
                }
                slot.count++;
                slot.totalNanos += Math.max(0, durationNanos);
            }
        }

        private long startSecond() {
            return startSecond;
        }

        private Stats stats() {
            long currentSecond = System.currentTimeMillis() / 1_000;
            long count = 0;
            long totalNanos = 0;
            for (Slot slot : slots) {
                synchronized (slot) {
                    if (slot.second > currentSecond - WINDOW_SECONDS
                            && slot.second <= currentSecond) {
                        count += slot.count;
                        totalNanos += slot.totalNanos;
                    }
                }
            }
            return new Stats(count, totalNanos);
        }

        private record Stats(long count, long totalNanos) {
        }

        private static final class Slot {
            private long second = -1;
            private long count;
            private long totalNanos;
        }
    }
}
