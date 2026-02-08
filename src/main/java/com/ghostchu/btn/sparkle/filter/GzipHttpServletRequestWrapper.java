package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class GzipHttpServletRequestWrapper extends HttpServletRequestWrapper {
    
    private final long zipBombThreshold;

    public GzipHttpServletRequestWrapper(@NotNull HttpServletRequest request, long zipBombThreshold) {
        super(request);
        this.zipBombThreshold = zipBombThreshold;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(super.getInputStream());
        GzipSizeLimitedInputStream gzipSizeLimitedInputStream = new GzipSizeLimitedInputStream(gzipInputStream, zipBombThreshold);
        return new GzipServletInputStreamWrapper(gzipSizeLimitedInputStream);
    }
}