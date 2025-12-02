package com.ghostchu.btn.sparkle.util;

public class HexUtil {

    public static String cutPeerId(String in) {
        if (in == null) return null;
        return in.substring(0, Math.min(in.length(), 20));
    }

    public static String sanitizeU0(String in) {
        if (in == null) return null;
        return in.replace("\u0000", "");
    }
}
