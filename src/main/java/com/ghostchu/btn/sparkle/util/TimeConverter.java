package com.ghostchu.btn.sparkle.util;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeConverter {
    public static final TimeConverter INSTANCE = new TimeConverter();

    public TimeConverter() {
    }

    public String formatTime(OffsetDateTime time, String zoneId) {
        // format to YYYY-MM-DD HH:MM:SS
        ZoneId zone = ZoneId.of(zoneId);
        return time.atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String formatDuration(Duration duration) {
        return formatDuration(duration.toMillis());
    }

    public String formatDuration(long durationInMs) {
        long totalSeconds = durationInMs / 1000;
        long days = totalSeconds / (24 * 3600);
        totalSeconds %= (24 * 3600);
        long hours = totalSeconds / 3600;
        totalSeconds %= 3600;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("秒");
        }
        return sb.toString();
    }


}
