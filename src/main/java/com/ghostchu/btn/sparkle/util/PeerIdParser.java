package com.ghostchu.btn.sparkle.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * BitTorrent Peer ID Parser
 * 基于 webtorrent/bittorrent-peerid 实现
 */
@SuppressWarnings("ALL")
public class PeerIdParser {

    public enum PeerIdType {
        AZ,      // Azureus style: -XX####-
        SHADOW,  // Shadow style: X####-
        MAINLINE,// Mainline style: M#-#-#--
        CUSTOM   // Custom/Simple style
    }

    public static class ParsedPeerId {
        private final String peerId;      // 不含随机串的特征部分，如 "-qB4520-"
        private final int major;
        private final int minor;
        private final int patch;
        private final int hotpatch;
        private final PeerIdType type;
        private final String client;

        public ParsedPeerId(String peerId, int major, int minor, int patch, int hotpatch, PeerIdType type, String client) {
            this.peerId = peerId;
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.hotpatch = hotpatch;
            this.type = type;
            this.client = client;
        }

        public String getPeerId() {
            return peerId;
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public int getPatch() {
            return patch;
        }

        public int getHotpatch() {
            return hotpatch;
        }

        public PeerIdType getType() {
            return type;
        }

        public String getClient() {
            return client;
        }

        @Override
        public String toString() {
            return String.format("ParsedPeerId{peerId='%s', client='%s', version=%d.%d.%d.%d, type=%s}",
                    peerId, client, major, minor, patch, hotpatch, type);
        }
    }

    private static final Map<String, String> AZ_STYLE_CLIENTS = new HashMap<>();
    private static final Map<String, String> SHADOW_STYLE_CLIENTS = new HashMap<>();
    private static final Map<String, String> MAINLINE_STYLE_CLIENTS = new HashMap<>();
    private static final Map<String, String> SIMPLE_CLIENTS = new HashMap<>();

    static {
        // Azureus style clients (双字符标识)
        AZ_STYLE_CLIENTS.put("AG", "Ares");
        AZ_STYLE_CLIENTS.put("AZ", "Vuze");
        AZ_STYLE_CLIENTS.put("TR", "Transmission");
        AZ_STYLE_CLIENTS.put("UT", "µTorrent");
        AZ_STYLE_CLIENTS.put("UM", "µTorrent Mac");
        AZ_STYLE_CLIENTS.put("UW", "µTorrent Web");
        AZ_STYLE_CLIENTS.put("qB", "qBittorrent");
        AZ_STYLE_CLIENTS.put("DE", "Deluge");
        AZ_STYLE_CLIENTS.put("LT", "libtorrent (Rasterbar)");
        AZ_STYLE_CLIENTS.put("lt", "libTorrent (Rakshasa)");
        AZ_STYLE_CLIENTS.put("WW", "WebTorrent");
        AZ_STYLE_CLIENTS.put("WD", "WebTorrent Desktop");
        AZ_STYLE_CLIENTS.put("BC", "BitComet");
        AZ_STYLE_CLIENTS.put("BT", "BitTorrent");
        AZ_STYLE_CLIENTS.put("KT", "KTorrent");
        AZ_STYLE_CLIENTS.put("MG", "MediaGet");
        AZ_STYLE_CLIENTS.put("PC", "CacheLogic");
        AZ_STYLE_CLIENTS.put("WY", "FireTorrent");
        AZ_STYLE_CLIENTS.put("SP", "BitSpirit");
        AZ_STYLE_CLIENTS.put("LP", "Lphant");
        AZ_STYLE_CLIENTS.put("LW", "LimeWire");
        AZ_STYLE_CLIENTS.put("MO", "MonoTorrent");
        AZ_STYLE_CLIENTS.put("SD", "迅雷在线");
        AZ_STYLE_CLIENTS.put("XL", "迅雷在线");
        AZ_STYLE_CLIENTS.put("FG", "FlashGet");
        AZ_STYLE_CLIENTS.put("HL", "Halite");
        AZ_STYLE_CLIENTS.put("PT", "Popcorn Time");
        AZ_STYLE_CLIENTS.put("7T", "aTorrent");
        AZ_STYLE_CLIENTS.put("AN", "Ares");
        AZ_STYLE_CLIENTS.put("AR", "Ares");
        AZ_STYLE_CLIENTS.put("AV", "Avicora");
        AZ_STYLE_CLIENTS.put("AX", "BitPump");
        AZ_STYLE_CLIENTS.put("AT", "Artemis");
        AZ_STYLE_CLIENTS.put("BB", "BitBuddy");
        AZ_STYLE_CLIENTS.put("BE", "BitTorrent SDK");
        AZ_STYLE_CLIENTS.put("BF", "BitFlu");
        AZ_STYLE_CLIENTS.put("BG", "BTG");
        AZ_STYLE_CLIENTS.put("bk", "BitKitten");
        AZ_STYLE_CLIENTS.put("BR", "BitRocket");
        AZ_STYLE_CLIENTS.put("BS", "BTSlave");
        AZ_STYLE_CLIENTS.put("BW", "BitWombat");
        AZ_STYLE_CLIENTS.put("BX", "BittorrentX");
        AZ_STYLE_CLIENTS.put("BL", "BiglyBT");
        AZ_STYLE_CLIENTS.put("CB", "Shareaza Plus");
        AZ_STYLE_CLIENTS.put("CD", "Enhanced CTorrent");
        AZ_STYLE_CLIENTS.put("CT", "CTorrent");
        AZ_STYLE_CLIENTS.put("DP", "Propogate Data Client");
        AZ_STYLE_CLIENTS.put("EB", "EBit");
        AZ_STYLE_CLIENTS.put("ES", "Electric Sheep");
        AZ_STYLE_CLIENTS.put("FC", "FileCroc");
        AZ_STYLE_CLIENTS.put("FX", "Freebox BitTorrent");
        AZ_STYLE_CLIENTS.put("FT", "FoxTorrent");
        AZ_STYLE_CLIENTS.put("GR", "GetRight");
        AZ_STYLE_CLIENTS.put("GS", "GSTorrent");
        AZ_STYLE_CLIENTS.put("HN", "Hydranode");
        AZ_STYLE_CLIENTS.put("KG", "KGet");
        AZ_STYLE_CLIENTS.put("LC", "LeechCraft");
        AZ_STYLE_CLIENTS.put("LH", "LH-ABC");
        AZ_STYLE_CLIENTS.put("LK", "linkage");
        AZ_STYLE_CLIENTS.put("MR", "Miro");
        AZ_STYLE_CLIENTS.put("MT", "MoonlightTorrent");
        AZ_STYLE_CLIENTS.put("NE", "BT Next Evolution");
        AZ_STYLE_CLIENTS.put("NX", "Net Transport");
        AZ_STYLE_CLIENTS.put("OS", "OneSwarm");
        AZ_STYLE_CLIENTS.put("OT", "OmegaTorrent");
        AZ_STYLE_CLIENTS.put("PD", "Pando");
        AZ_STYLE_CLIENTS.put("PE", "PeerProject");
        AZ_STYLE_CLIENTS.put("pX", "pHoeniX");
        AZ_STYLE_CLIENTS.put("QD", "qqdownload");
        AZ_STYLE_CLIENTS.put("RM", "RUM Torrent");
        AZ_STYLE_CLIENTS.put("RT", "Retriever");
        AZ_STYLE_CLIENTS.put("RZ", "RezTorrent");
        AZ_STYLE_CLIENTS.put("SB", "SwiftBit");
        AZ_STYLE_CLIENTS.put("SG", "GS Torrent");
        AZ_STYLE_CLIENTS.put("SN", "ShareNET");
        AZ_STYLE_CLIENTS.put("SS", "SwarmScope");
        AZ_STYLE_CLIENTS.put("ST", "SymTorrent");
        AZ_STYLE_CLIENTS.put("st", "SharkTorrent");
        AZ_STYLE_CLIENTS.put("SZ", "Shareaza");
        AZ_STYLE_CLIENTS.put("TG", "Torrent GO");
        AZ_STYLE_CLIENTS.put("TN", "Torrent. NET");
        AZ_STYLE_CLIENTS.put("TS", "TorrentStorm");
        AZ_STYLE_CLIENTS.put("TT", "TuoTu");
        AZ_STYLE_CLIENTS.put("UL", "uLeecher!");
        AZ_STYLE_CLIENTS.put("UE", "µTorrent Embedded");
        AZ_STYLE_CLIENTS.put("WT", "Bitlet");
        AZ_STYLE_CLIENTS.put("VG", "哇嘎");
        AZ_STYLE_CLIENTS.put("XT", "XanTorrent");
        AZ_STYLE_CLIENTS.put("XF", "Xfplay");
        AZ_STYLE_CLIENTS.put("XX", "XTorrent");
        AZ_STYLE_CLIENTS.put("XC", "XTorrent");
        AZ_STYLE_CLIENTS.put("ZT", "ZipTorrent");
        AZ_STYLE_CLIENTS.put("ZO", "Zona");

        // Shadow style clients (单字符标识)
        SHADOW_STYLE_CLIENTS.put("A", "Aria2");
        SHADOW_STYLE_CLIENTS.put("O", "Osprey Permaseed");
        SHADOW_STYLE_CLIENTS.put("Q", "BTQueue");
        SHADOW_STYLE_CLIENTS.put("R", "Tribler");
        SHADOW_STYLE_CLIENTS.put("S", "Shad0w");
        SHADOW_STYLE_CLIENTS.put("T", "BitTornado");
        SHADOW_STYLE_CLIENTS.put("U", "UPnP NAT");

        // Mainline style clients
        MAINLINE_STYLE_CLIENTS.put("M", "Mainline");
        MAINLINE_STYLE_CLIENTS.put("Q", "Queen Bee");

        // Simple/Custom clients (使用更短的特征字符串)
        SIMPLE_CLIENTS.put("aria2", "Aria");
        SIMPLE_CLIENTS.put("DNA", "BitTorrent DNA");
        SIMPLE_CLIENTS.put("exbc", "BitComet");
        SIMPLE_CLIENTS.put("FUTB", "BitComet");
        SIMPLE_CLIENTS.put("xUTB", "BitComet");
        SIMPLE_CLIENTS.put("Pando", "Pando");
        SIMPLE_CLIENTS.put("QVOD", "QVOD");
        SIMPLE_CLIENTS.put("TB", "Top-BT");
        SIMPLE_CLIENTS.put("TIX", "Tixati");
        SIMPLE_CLIENTS.put("BTM", "BTuga Revolution");
        SIMPLE_CLIENTS.put("RS", "Rufus");
        SIMPLE_CLIENTS.put("BM", "BitMagnet");
        SIMPLE_CLIENTS.put("LIME", "Limewire");
        SIMPLE_CLIENTS.put("btfans", "SimpleBT");
        SIMPLE_CLIENTS.put("XBT", "XBT");
        SIMPLE_CLIENTS.put("Ext", "External Webseed");
        SIMPLE_CLIENTS.put("BLZ", "Blizzard Downloader");
        SIMPLE_CLIENTS.put("btpd", "BT Protocol Daemon");
        SIMPLE_CLIENTS.put("Plus", "Plus!");
        SIMPLE_CLIENTS.put("turbobt", "TurboBT");
        SIMPLE_CLIENTS.put("A2", "Aria2");
        SIMPLE_CLIENTS.put("FD6", "Free Download Manager 6");
        SIMPLE_CLIENTS.put("FD5", "Free Download Manager 5");
    }

    /**
     * 解析 Peer ID
     *
     * @param peerIdBytes Peer ID 字节数组（会自动处理不是20字节的情况）
     * @return ParsedPeerId 对象，如果无法解析则返回 null
     */
    public static ParsedPeerId parse(byte[] peerIdBytes) {
        if (peerIdBytes == null || peerIdBytes.length == 0) {
            return null;
        }

        // 处理长度问题：截取或补齐到 20 字节
        byte[] normalizedBytes = new byte[20];
        if (peerIdBytes.length >= 20) {
            System.arraycopy(peerIdBytes, 0, normalizedBytes, 0, 20);
        } else {
            // 如果不足 20 字节，复制实际长度并用 0 填充剩余部分
            System.arraycopy(peerIdBytes, 0, normalizedBytes, 0, peerIdBytes.length);
            for (int i = peerIdBytes.length; i < 20; i++) {
                normalizedBytes[i] = 0;
            }
        }

        // 尝试 UTF-8 解码
        String peerId = new String(normalizedBytes, StandardCharsets.UTF_8);

        // 检查 Azureus style
        if (isAzStyle(peerId)) {
            return parseAzStyle(peerId, normalizedBytes);
        }

        // 检查 Shadow style
        if (isShadowStyle(peerId)) {
            return parseShadowStyle(peerId);
        }

        // 检查 Mainline style
        if (isMainlineStyle(peerId)) {
            return parseMainlineStyle(peerId);
        }

        // 检查 BitSpirit
        if (peerId.length() > 3 && peerId.substring(2, 4).equals("BS")) {
            return parseBitSpirit(peerId, normalizedBytes);
        }

        // 检查 BitComet/BitLord
        if (peerId.startsWith("exbc") || peerId.startsWith("FUTB") || peerId.startsWith("xUTB")) {
            return parseBitComet(peerId, normalizedBytes);
        }

        // 检查简单客户端
        ParsedPeerId simple = parseSimpleClient(peerId);
        if (simple != null) {
            return simple;
        }

        // 检查特殊客户端（Shareaza 等）
        ParsedPeerId awkward = identifyAwkwardClient(normalizedBytes);
        if (awkward != null) {
            return awkward;
        }

        return null;
    }

    /**
     * 便捷方法：从字符串解析
     *
     * @param peerIdString Peer ID 字符串（支持 UTF-8 或 十六进制格式）
     * @return ParsedPeerId 对象，如果无法解析则返回 null
     */
    public static ParsedPeerId parse(String peerIdString) {
        if (peerIdString == null || peerIdString.isEmpty()) {
            return null;
        }

        byte[] bytes;

        // 判断是否为十六进制字符串（仅包含 0-9, a-f, A-F）
        if (isHexString(peerIdString)) {
            bytes = hexStringToByteArray(peerIdString);
        } else {
            // 作为 UTF-8 字符串处理
            bytes = peerIdString.getBytes(StandardCharsets.UTF_8);
        }

        return parse(bytes);
    }

    private static boolean isAzStyle(String peerId) {
        if (peerId.length() < 3) return false;
        if (peerId.charAt(0) != '-') return false;
        if (peerId.length() > 7 && peerId.charAt(7) == '-') return true;

        // 特殊处理某些客户端（它们不使用结尾的横杠）
        if (peerId.length() >= 3) {
            String id = peerId.substring(1, 3);
            return id.equals("FG") || id.equals("LH") || id.equals("NE") ||
                    id.equals("KT") || id.equals("SP");
        }
        return false;
    }

    private static boolean isShadowStyle(String peerId) {
        if (peerId.length() < 6) return false;
        if (peerId.charAt(5) != '-') return false;
        if (!Character.isLetter(peerId.charAt(0))) return false;

        char second = peerId.charAt(1);
        if (!(Character.isDigit(second) || second == '-')) return false;

        // 验证版本号部分
        for (int i = 1; i < 5; i++) {
            char c = peerId.charAt(i);
            if (c == '-') continue;
            if (!Character.isLetterOrDigit(c) && c != '.') return false;
        }

        return true;
    }

    private static boolean isMainlineStyle(String peerId) {
        if (peerId.length() < 8) return false;
        return peerId.charAt(2) == '-' && peerId.charAt(7) == '-' &&
                (peerId.charAt(4) == '-' || peerId.charAt(5) == '-');
    }

    private static ParsedPeerId parseAzStyle(String peerId, byte[] bytes) {
        if (peerId.length() < 3) return null;

        String clientId = peerId.substring(1, 3);
        String client = AZ_STYLE_CLIENTS.get(clientId);

        // 提取 PeerID 特征部分：-XX####- (8个字符)
        // 如果第7位是横杠，就取前8位；否则取前7位（某些客户端没有结尾横杠）
        String peerIdPrefix;
        if (peerId.length() >= 8 && peerId.charAt(7) == '-') {
            peerIdPrefix = peerId.substring(0, 8);  // 例如：-qB4520-
        } else if (peerId.length() >= 7) {
            peerIdPrefix = peerId.substring(0, 7);  // 例如：-FG1234（FlashGet 等）
        } else {
            peerIdPrefix = peerId.substring(0, Math.min(peerId.length(), 8));
        }

        // 解析版本号 (位置 3-6)，4个字符对应 major/minor/patch/hotpatch
        int major = 0, minor = 0, patch = 0, hotpatch = 0;

        try {
            if (peerId.length() >= 7) {
                String versionStr = peerId.substring(3, 7);

                // 标准格式：4个字符，每个字符表示一个版本号部分 (0-Z，其中 F=16)
                major = parseVersionChar(versionStr.charAt(0));
                minor = parseVersionChar(versionStr.charAt(1));
                patch = parseVersionChar(versionStr.charAt(2));
                hotpatch = parseVersionChar(versionStr.charAt(3));
            }
        } catch (Exception e) {
            // 版本解析失败，保持为 0
        }

        return new ParsedPeerId(peerIdPrefix, major, minor, patch, hotpatch, PeerIdType.AZ, client);
    }

    private static ParsedPeerId parseShadowStyle(String peerId) {
        String clientId = peerId.substring(0, 1);
        String client = SHADOW_STYLE_CLIENTS.get(clientId);

        // 提取 PeerID 特征部分：X####- (6个字符)
        String peerIdPrefix = peerId.substring(0, 6);  // 例如：T03---

        // 尝试解析版本号 (Shadow style 通常是4个字符的版本号)
        int major = 0, minor = 0, patch = 0, hotpatch = 0;
        try {
            if (peerId.length() > 1 && Character.isLetterOrDigit(peerId.charAt(1))) {
                major = parseVersionChar(peerId.charAt(1));
            }
            if (peerId.length() > 2 && Character.isLetterOrDigit(peerId.charAt(2))) {
                minor = parseVersionChar(peerId.charAt(2));
            }
            if (peerId.length() > 3 && Character.isLetterOrDigit(peerId.charAt(3))) {
                patch = parseVersionChar(peerId.charAt(3));
            }
            if (peerId.length() > 4 && Character.isLetterOrDigit(peerId.charAt(4))) {
                hotpatch = parseVersionChar(peerId.charAt(4));
            }
        } catch (Exception e) {
            // 忽略解析错误
        }

        return new ParsedPeerId(peerIdPrefix, major, minor, patch, hotpatch, PeerIdType.SHADOW, client);
    }

    private static ParsedPeerId parseMainlineStyle(String peerId) {
        String clientId = peerId.substring(0, 1);
        String client = MAINLINE_STYLE_CLIENTS.get(clientId);

        // 提取 PeerID 特征部分：M#-#-#-- 或 M#-##-#- (8个字符)
        String peerIdPrefix = peerId.substring(0, 8);  // 例如：M7-0-2--

        // Mainline 格式: Mx-y-z-- 或 Mx-yy-z- (保留原有格式，hotpatch 为 0)
        int major = 0, minor = 0, patch = 0, hotpatch = 0;
        try {
            major = parseVersionChar(peerId.charAt(1));
            if (peerId.charAt(4) == '-') {
                // Mx-y-z-- 格式
                minor = parseVersionChar(peerId.charAt(3));
                patch = parseVersionChar(peerId.charAt(5));
            } else {
                // Mx-yy-z- 格式
                minor = Integer.parseInt(peerId.substring(3, 5));
                patch = parseVersionChar(peerId.charAt(6));
            }
        } catch (Exception e) {
            // 忽略解析错误
        }

        return new ParsedPeerId(peerIdPrefix, major, minor, patch, hotpatch, PeerIdType.MAINLINE, client);
    }

    private static ParsedPeerId parseBitSpirit(String peerId, byte[] peerIdBytes) {
        // BitSpirit 格式：通常前6个字符
        String peerIdPrefix = peerId.substring(0, Math.min(6, peerId.length()));  // 例如：--BS12

        int major = (peerIdBytes.length > 1) ? (peerIdBytes[1] & 0xFF) : 0;
        if (major == 0) major = 1;

        return new ParsedPeerId(peerIdPrefix, major, 0, 0, 0, PeerIdType.CUSTOM, "BitSpirit");
    }

    private static ParsedPeerId parseBitComet(String peerId, byte[] peerIdBytes) {
        boolean isBitlord = peerId.length() >= 10 && peerId.substring(6, 10).equals("LORD");
        String client = isBitlord ? "BitLord" : "BitComet";

        // BitComet/BitLord 格式：前10个字符包含标识
        String peerIdPrefix = peerId.substring(0, Math.min(10, peerId.length()));  // 例如：exbc.. LORD

        int major = (peerIdBytes.length > 4) ? (peerIdBytes[4] & 0xFF) : 0;
        int minor = (peerIdBytes.length > 5) ? (peerIdBytes[5] & 0xFF) : 0;

        return new ParsedPeerId(peerIdPrefix, major, minor, 0, 0, PeerIdType.CUSTOM, client);
    }

    private static ParsedPeerId parseSimpleClient(String peerId) {
        for (Map.Entry<String, String> entry : SIMPLE_CLIENTS.entrySet()) {
            String key = entry.getKey();
            if (peerId.contains(key)) {
                // 使用匹配到的特征字符串作为 PeerID 前缀
                return new ParsedPeerId(key, 0, 0, 0, 0, PeerIdType.CUSTOM, entry.getValue());
            }
        }
        return null;
    }

    private static ParsedPeerId identifyAwkwardClient(byte[] buffer) {
        // 查找第一个非零字节
        int firstNonZeroIndex = 20;
        for (int i = 0; i < buffer.length && i < 20; i++) {
            if (buffer[i] != 0) {
                firstNonZeroIndex = i;
                break;
            }
        }

        // Shareaza 检测
        if (firstNonZeroIndex == 0 && buffer.length >= 20) {
            boolean isShareaza = true;
            for (int i = 0; i < 16; i++) {
                if (buffer[i] == 0) {
                    isShareaza = false;
                    break;
                }
            }

            if (isShareaza) {
                for (int i = 16; i < 20; i++) {
                    if (buffer[i] != (buffer[i % 16] ^ buffer[15 - (i % 16)])) {
                        isShareaza = false;
                        break;
                    }
                }

                if (isShareaza) {
                    // Shareaza 使用前16个字节作为标识
                    String prefix = bytesToHex(buffer, 0, 8);  // 取前8字节的十六进制表示
                    return new ParsedPeerId(prefix, 0, 0, 0, 0, PeerIdType.CUSTOM, "Shareaza");
                }
            }
        }

        // I2PSnark 检测
        if (firstNonZeroIndex == 9 && buffer.length >= 12 &&
                buffer[9] == 3 && buffer[10] == 3 && buffer[11] == 3) {
            return new ParsedPeerId("I2P", 0, 0, 0, 0, PeerIdType.CUSTOM, "I2PSnark");
        }

        // Mainline 特殊检测
        if (firstNonZeroIndex == 12 && buffer.length >= 14) {
            if (buffer[12] == 97 && buffer[13] == 97) {
                return new ParsedPeerId("Exp", 3, 2, 1, 0, PeerIdType.CUSTOM, "Experimental");
            }
            if (buffer[12] == 0 && buffer[13] == 0) {
                return new ParsedPeerId("Exp", 3, 1, 0, 0, PeerIdType.CUSTOM, "Experimental");
            }
            return new ParsedPeerId("ML", 0, 0, 0, 0, PeerIdType.CUSTOM, "Mainline");
        }

        return null;
    }

    private static int parseVersionChar(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        // 处理字母版本号 (如 Deluge 的 'A' = 10, 'B' = 11, ...)
        if (Character.isUpperCase(c)) {
            return c - 'A' + 10;
        }
        if (Character.isLowerCase(c)) {
            return c - 'a' + 10;
        }
        return 0;
    }

    private static boolean isHexString(String s) {
        if (s.length() % 2 != 0) return false;
        for (char c : s.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexStringToByteArray(String s) {
        try {
            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i + 1), 16));
            }
            return data;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length && i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
