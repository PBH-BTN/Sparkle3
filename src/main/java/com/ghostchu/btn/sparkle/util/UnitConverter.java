package com.ghostchu.btn.sparkle.util;

public class UnitConverter {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};
    private static final long BASE = 1024;
    public static final UnitConverter INSTANCE = new UnitConverter();

    public UnitConverter() {

    }

    public static String autoUnit(long bytes) {
        return INSTANCE.formatSize(bytes);
    }

    public String formatSize(long bytes) {
        if (bytes == 0) return "0.00 B";
        int unitIndex = (int) (Math.log(bytes) / Math.log(BASE));
        unitIndex = Math.min(unitIndex, UNITS.length - 1); // 防止越界
        double value = bytes / Math.pow(BASE, unitIndex);
        return String.format("%.2f %s", value, UNITS[unitIndex]);
    }


}