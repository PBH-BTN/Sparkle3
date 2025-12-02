package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class GzipHttpServletRequestWrapper extends HttpServletRequestWrapper {

    // 32MB 解压缩限制，防止 zip-bomb 攻击
    private static final long MAX_DECOMPRESSED_SIZE = 16 * 1024 * 1024; // 32MB

    public GzipHttpServletRequestWrapper(@NotNull HttpServletRequest request) {
        super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(super.getInputStream());
        GzipSizeLimitedInputStream gzipSizeLimitedInputStream = new GzipSizeLimitedInputStream(gzipInputStream, MAX_DECOMPRESSED_SIZE);
        return new GzipServletInputStreamWrapper(gzipSizeLimitedInputStream);
    }
}