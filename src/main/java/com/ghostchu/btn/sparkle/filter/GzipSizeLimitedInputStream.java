package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.ServletInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 带大小限制的输入流，防止 zip-bomb 攻击
 */
class GzipSizeLimitedInputStream extends FilterInputStream {

    private final long maxSize;
    private final ServletInputStream servletInputStream;
    private long totalBytesRead = 0;

    public GzipSizeLimitedInputStream(ServletInputStream inputStream, @NotNull InputStream in, long maxSize) {
        super(in);
        this.servletInputStream = inputStream;
        this.maxSize = maxSize;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            totalBytesRead++;
            checkLimit();
        }
        return b;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
        int bytesRead = super.read(b, off, len);
        if (bytesRead > 0) {
            totalBytesRead += bytesRead;
            checkLimit();
        }
        return bytesRead;
    }

    @Override
    public int read(byte @NotNull [] b) throws IOException {
        return read(b, 0, b.length);
    }

    private void checkLimit() throws IOException {
        if (totalBytesRead > maxSize) {
            throw new IOException(String.format(
                    "解压缩数据超过限制: 已解压 %d 字节数据, 原始 servlet 流缓冲区仍剩余 %d 压缩的字节 (最大允许: %d 未压缩字节)。Zip-Bomb?",
                    totalBytesRead, servletInputStream.available(), maxSize
            ));
        }
    }

    public long getTotalBytesRead() {
        return totalBytesRead;
    }
}
