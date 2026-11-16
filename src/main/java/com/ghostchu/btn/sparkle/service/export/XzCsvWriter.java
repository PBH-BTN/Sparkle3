package com.ghostchu.btn.sparkle.service.export;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class XzCsvWriter {
    private static final int BLOCK_BYTES = 256 * 1024;
    private final OutputStream out;
    private final long maxBytes;
    private final Heartbeat heartbeat;
    private long bytes;
    private int rows;

    private XzCsvWriter(OutputStream out, long maxBytes, Heartbeat heartbeat) {
        this.out = out;
        this.maxBytes = maxBytes;
        this.heartbeat = heartbeat;
    }

    static Result write(Path destination, List<String> columns, Iterable<Map<String, Object>> records,
                        int maxRows, long maxBytes, Heartbeat heartbeat, GeoFormatter geo) throws IOException {
        try (var out = new BufferedOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            var writer = new XzCsvWriter(out, maxBytes, heartbeat);
            writer.append(compress(List.of(csv(columns))));
            var block = new ArrayList<byte[]>();
            long uncompressed = 0;
            var iterator = records.iterator();
            boolean more = iterator.hasNext();
            while (more && writer.bytes < maxBytes && writer.rows < maxRows) {
                heartbeat.check();
                byte[] line = encode(columns, iterator.next(), geo);
                block.add(line);
                uncompressed += line.length;
                if (uncompressed >= BLOCK_BYTES || writer.rows + block.size() >= maxRows) {
                    if (writer.writeBlock(block) < block.size()) {
                        return new Result(writer.rows, writer.bytes, true);
                    }
                    block.clear();
                    uncompressed = 0;
                }
                more = iterator.hasNext();
            }
            if (!block.isEmpty() && writer.writeBlock(block) < block.size()) {
                return new Result(writer.rows, writer.bytes, true);
            }
            return new Result(writer.rows, writer.bytes, more);
        }
    }

    // Concatenated XZ streams are independently finalized. Split only the segment
    // that meets the limit, so the final complete CSV record can cross it once.
    private int writeBlock(List<byte[]> block) throws IOException {
        heartbeat.check();
        byte[] compressed = compress(block);
        if (block.size() == 1 || compressed.length < maxBytes - bytes) {
            append(compressed);
            rows += block.size();
            return block.size();
        }
        int middle = block.size() / 2;
        int first = writeBlock(block.subList(0, middle));
        return bytes >= maxBytes ? first : first + writeBlock(block.subList(middle, block.size()));
    }

    private void append(byte[] stream) throws IOException {
        out.write(stream);
        bytes += stream.length;
    }

    private static byte[] compress(List<byte[]> lines) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var xz = new XZOutputStream(buffer, new LZMA2Options(1), org.tukaani.xz.XZ.CHECK_CRC64,
                org.tukaani.xz.ArrayCache.getDefaultCache())) {
            for (byte[] line : lines) xz.write(line);
        }
        return buffer.toByteArray();
    }

    private static byte[] encode(List<String> columns, Map<String, Object> row, GeoFormatter geo) throws IOException {
        var values = new ArrayList<String>(columns.size());
        for (String column : columns) {
            Object value = row.get(column);
            String text;
            if (value == null) text = "";
            else if (value instanceof Timestamp time) text = time.toInstant().toString();
            else if ("geo".equals(column)) text = geo.format(value);
            else if ("peer_ip".equals(column)) text = value.toString().split("/", 2)[0];
            else if ("module_name".equals(column)) {
                String prefix = "com.ghostchu.peerbanhelper.module.impl.rule.";
                String name = value.toString();
                text = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
            } else text = value.toString();
            values.add(value instanceof Number ? text : protectFormula(text));
        }
        return csv(values);
    }

    private static String protectFormula(String value) {
        String trimmed = value.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@＝＋－＠".indexOf(trimmed.charAt(0)) >= 0)
                || (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0)) {
            return "'" + value;
        }
        return value;
    }

    private static byte[] csv(List<String> values) {
        return (String.join(",", values.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"").toList())
                + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    interface Heartbeat {
        void check() throws IOException;
    }

    @FunctionalInterface
    interface GeoFormatter {
        String format(Object value) throws IOException;
    }

    record Result(int rows, long bytes, boolean truncated) {
    }
}
