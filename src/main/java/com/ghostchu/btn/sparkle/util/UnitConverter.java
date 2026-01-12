package com.ghostchu.btn.sparkle.util;

public class UnitConverter {

    public static final UnitConverter INSTANCE = new UnitConverter();

    public UnitConverter() {

    }

    public static String autoUnit(long bytes) {
        return MsgUtil.humanReadableByteCountBin(bytes);
    }

    public String formatSize(long bytes) {
        return MsgUtil.humanReadableByteCountBin(bytes);
    }


}