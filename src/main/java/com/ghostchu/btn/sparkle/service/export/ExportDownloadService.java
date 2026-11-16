package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportDownloadService {
    private final DataExportFiles files;
    private final DataExportSettings settings;
    private final ExportDownloadLimiter limiter;

    public void download(DataExportJob job, HttpServletRequest request, HttpServletResponse response)
            throws BusinessException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        Path file = files.ready(job.id());
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "export_download_unavailable");
        }

        long length = job.fileBytes();
        String tag = "\"" + job.id() + "\"";
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.ETAG, tag);
        Segment segment;
        try {
            segment = segment(request.getHeader(HttpHeaders.RANGE), request.getHeader(HttpHeaders.IF_RANGE), tag, length);
        } catch (IllegalArgumentException exception) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return;
        }

        boolean head = "HEAD".equals(request.getMethod());
        if (head) {
            writeHeaders(job, response, segment, length);
            return;
        }

        int mbps;
        String lease;
        try {
            mbps = settings.get().downloadMbps();
            lease = limiter.acquire(job.userId());
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "export_download_busy");
        }
        if (lease == null) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "export_download_connections");
        }
        AsyncContext context;
        try {
            context = request.startAsync();
        } catch (RuntimeException exception) {
            release(job.userId(), lease);
            throw exception;
        }
        long remainingMillis = Duration.between(DataExportService.now(), job.expiresAt()).toMillis();
        context.setTimeout(Math.max(1L, remainingMillis));
        Thread.startVirtualThread(() -> stream(context, response, job, file, segment, lease, mbps));
    }

    private void stream(AsyncContext context, HttpServletResponse response, DataExportJob job, Path file,
                        Segment segment, String lease, int mbps) {
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            writeHeaders(job, response, segment, job.fileBytes());
            channel.position(segment.start());
            var out = response.getOutputStream();
            Semaphore writable = new Semaphore(0);
            AtomicBoolean disconnected = new AtomicBoolean();
            out.setWriteListener(new WriteListener() {
                @Override
                public void onWritePossible() {
                    writable.release();
                }

                @Override
                public void onError(Throwable error) {
                    disconnected.set(true);
                    writable.release();
                }
            });
            long remaining = segment.size();
            var buffer = ByteBuffer.allocate(ExportDownloadLimiter.CHUNK_BYTES);
            while (remaining > 0) {
                buffer.clear();
                buffer.limit((int) Math.min(remaining, buffer.capacity()));
                int read = channel.read(buffer);
                if (read < 1) throw new IOException("Incomplete export file");
                while (!out.isReady()) {
                    if (disconnected.get() || !job.expiresAt().isAfter(DataExportService.now())
                            || !writable.tryAcquire(45, TimeUnit.SECONDS)) {
                        throw new IOException("Inactive export download");
                    }
                }
                if (disconnected.get()) throw new IOException("Disconnected export download");
                awaitBudget(job, lease, read, mbps);
                if (disconnected.get()) throw new IOException("Disconnected export download");
                out.write(buffer.array(), 0, read);
                remaining -= read;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            // Disconnects and expired leases are common. Avoid logging request URLs or tokens.
            log.debug("Export transfer ended before completion ({})", exception.getClass().getSimpleName());
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.setContentLength(0);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        } finally {
            try {
                context.complete();
            } catch (IllegalStateException ignored) {
                // The container may have completed an expired request.
            } finally {
                release(job.userId(), lease);
            }
        }
    }

    private void awaitBudget(DataExportJob job, String lease, int bytes, int mbps) throws IOException, InterruptedException {
        while (job.expiresAt().isAfter(DataExportService.now())) {
            long wait = limiter.waitMillis(job.userId(), lease, bytes, mbps);
            if (wait == 0) return;
            if (wait < 0) break;
            Thread.sleep(wait);
        }
        throw new IOException("Export download expired");
    }

    private void release(long uid, String lease) {
        try {
            limiter.release(uid, lease);
        } catch (RuntimeException exception) {
            log.debug("Download lease will expire automatically");
        }
    }

    private static void writeHeaders(DataExportJob job, HttpServletResponse response, Segment segment, long length) {
        response.setContentType("application/x-xz");
        response.setContentLengthLong(segment.size());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sparkle-" + job.category().name()
                .toLowerCase(java.util.Locale.ROOT) + "-" + job.id() + ".csv.xz\"");
        if (segment.partial()) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + segment.start() + "-" + segment.end() + "/" + length);
        }
    }

    static Segment segment(String range, String ifRange, String tag, long length) {
        if (range == null || (ifRange != null && !tag.equals(ifRange))) {
            return new Segment(0, length - 1, false);
        }
        List<HttpRange> ranges = HttpRange.parseRanges(range);
        if (ranges.size() != 1) throw new IllegalArgumentException("One byte range is supported per request");
        long start = ranges.getFirst().getRangeStart(length);
        long end = ranges.getFirst().getRangeEnd(length);
        if (start < 0 || start >= length || end < start) throw new IllegalArgumentException("Unsatisfiable byte range");
        return new Segment(start, end, true);
    }

    record Segment(long start, long end, boolean partial) {
        long size() {
            return end - start + 1;
        }
    }
}
