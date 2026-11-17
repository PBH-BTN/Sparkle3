package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Category;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Settings;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "SPARKLE_EXPORT_TEST_REDIS_PORT", matches = "[0-9]+")
class ExportDownloadIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void differentInstancesShareTheThirtyTwoConnectionLimit() {
        try (var fixture = redis()) {
            var first = new ExportDownloadLimiter(fixture.template());
            var second = new ExportDownloadLimiter(fixture.template());
            long uid = 1_001;
            var leases = new ArrayList<String>();
            for (int i = 0; i < 32; i++) {
                String lease = (i % 2 == 0 ? first : second).acquire(uid);
                assertNotNull(lease);
                leases.add(lease);
            }
            assertNull(second.acquire(uid));
            String separate = second.acquire(1_002);
            assertNotNull(separate);
            second.release(1_002, separate);
            first.release(uid, leases.removeFirst());
            String reopened = second.acquire(uid);
            assertNotNull(reopened);
            leases.add(reopened);
            for (String lease : leases) first.release(uid, lease);
        }
    }

    @Test
    void parallelHttpRangesShareTheByteBudgetAcrossFiles() throws Exception {
        try (var fixture = redis()) {
            var files = new DataExportFiles(directory.toString());
            var settings = mock(DataExportSettings.class);
            when(settings.get()).thenReturn(new Settings(100, 10, 72, 8, 5, 100, "", ""));
            var first = new ExportDownloadService(files, settings, new ExportDownloadLimiter(fixture.template()));
            var second = new ExportDownloadService(files, settings, new ExportDownloadLimiter(fixture.template()));
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            byte[] content = new byte[524_288];
            new java.util.Random(1).nextBytes(content);
            Files.write(files.ready(firstId), content);
            Files.write(files.ready(secondId), content);
            var tomcat = new Tomcat();
            tomcat.setBaseDir(directory.resolve("tomcat").toString());
            tomcat.setPort(0);
            tomcat.getConnector().setProperty("compression", "force");
            var context = tomcat.addContext("", directory.toString());
            var servlet = Tomcat.addServlet(context, "download", new HttpServlet() {
                @Override
                protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    boolean a = "/a".equals(request.getRequestURI());
                    var now = DataExportService.now();
                    var job = new DataExportJob(a ? firstId : secondId, 2_001, Category.BANS, "{}", "en",
                            now, 100, 1024, 72, "READY", now.plusMinutes(1), (long) content.length, "fixture");
                    try {
                        (a ? first : second).download(job, request, response);
                    } catch (BusinessException exception) {
                        response.sendError(exception.getStatusCode().value());
                    }
                }
            });
            servlet.setAsyncSupported(true);
            context.addServletMappingDecoded("/*", "download");
            try {
                tomcat.start();
                String url = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort();
                try (var client = HttpClient.newHttpClient()) {
                    URI file = URI.create(url + "/a");
                    var full = client.send(HttpRequest.newBuilder(file).header("Accept-Encoding", "gzip").build(),
                            HttpResponse.BodyHandlers.ofInputStream());
                    assertEquals(200, full.statusCode());
                    assertEquals("524288", full.headers().firstValue("Content-Length").orElseThrow());
                    assertEquals("bytes", full.headers().firstValue("Accept-Ranges").orElseThrow());
                    assertTrue(full.headers().firstValue("Cache-Control").orElseThrow().contains("no-transform"));
                    try (var body = full.body()) {
                        assertArrayEquals(java.util.Arrays.copyOf(content, 262_144), body.readNBytes(262_144));
                    }
                    var head = client.send(HttpRequest.newBuilder(file).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.discarding());
                    assertEquals("524288", head.headers().firstValue("Content-Length").orElseThrow());
                    String tag = full.headers().firstValue("ETag").orElseThrow();
                    var resumed = client.send(HttpRequest.newBuilder(file)
                                    .header("Accept-Encoding", "gzip").header("Range", "bytes=262144-")
                                    .header("If-Range", tag).build(), HttpResponse.BodyHandlers.ofByteArray());
                    assertEquals(206, resumed.statusCode());
                    assertEquals("262144", resumed.headers().firstValue("Content-Length").orElseThrow());
                    assertEquals("bytes 262144-524287/524288", resumed.headers().firstValue("Content-Range").orElseThrow());
                    assertArrayEquals(java.util.Arrays.copyOfRange(content, 262_144, content.length), resumed.body());

                    List<CompletableFuture<HttpResponse<byte[]>>> pending = new ArrayList<>();
                    long started = System.nanoTime();
                    for (int i = 0; i < 4; i++) {
                        var request = HttpRequest.newBuilder(URI.create(url + (i < 2 ? "/a" : "/b")))
                                .timeout(Duration.ofSeconds(12))
                                .header("Range", "bytes=0-262143").build();
                        pending.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()));
                    }
                    for (var result : pending) {
                        var reply = result.get(12, TimeUnit.SECONDS);
                        assertEquals(206, reply.statusCode());
                        assertEquals("bytes 0-262143/524288", reply.headers().firstValue("Content-Range").orElseThrow());
                        assertArrayEquals(java.util.Arrays.copyOf(content, 262_144), reply.body());
                    }
                    assertTrue(System.nanoTime() - started >= Duration.ofMillis(900).toNanos());
                    var invalid = client.send(HttpRequest.newBuilder(URI.create(url + "/a"))
                                    .header("Range", "bytes=524288-").build(), HttpResponse.BodyHandlers.discarding());
                    assertEquals(416, invalid.statusCode());
                }
            } finally {
                tomcat.stop();
                tomcat.destroy();
            }
        }
    }

    private static RedisFixture redis() {
        var factory = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("SPARKLE_EXPORT_TEST_REDIS_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        return new RedisFixture(factory, new StringRedisTemplate(factory));
    }

    private record RedisFixture(LettuceConnectionFactory factory, StringRedisTemplate template) implements AutoCloseable {
        @Override
        public void close() {
            factory.destroy();
        }
    }
}
