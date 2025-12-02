package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

class GzipServletInputStreamWrapper extends ServletInputStream {

    private final InputStream delegate;

    public GzipServletInputStreamWrapper(@NotNull InputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setReadListener(ReadListener listener) {

    }

}