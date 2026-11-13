package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

@Service
public class AfdianMessageClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    public JsonNode dialogs(String token, int page) throws BusinessException {
        return get(token, "dialogs?page=" + page + "&unread=0");
    }

    public JsonNode messages(String token, String userId, String cursor) throws BusinessException {
        if (!userId.matches("[A-Za-z0-9_-]{1,128}") || !cursor.matches("[0-9]{1,30}")) throw failure();
        return get(token, "messages?user_id=" + userId + "&type=old&message_id=" + cursor);
    }

    private JsonNode get(String token, String query) throws BusinessException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://afdian.com/api/message/" + query))
                .timeout(Duration.ofSeconds(30)).header("Cookie", "auth_token=" + token)
                .header("Accept", "application/json").GET().build();
        var pending = client.sendAsync(request, ignored -> new LimitedBody());
        try {
            var response = pending.get(30, java.util.concurrent.TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw failure();
            JsonNode root = json.readTree(response.body());
            if (root.path("ec").asInt() != 200 || !root.path("data").path("list").isArray()) throw failure();
            return root.path("data");
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure();
        } catch (Exception exception) {
            throw failure();
        } finally {
            pending.cancel(true);
        }
    }

    private BusinessException failure() {
        return new BusinessException(HttpStatus.BAD_GATEWAY,
                "Unable to read Afdian messages. Check the session credential and retry.");
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int size;
        private boolean failed;

        @Override
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer buffer : buffers) {
                size += buffer.remaining();
                if (size > 4 * 1024 * 1024) {
                    failed = true;
                    subscription.cancel();
                    delegate.onError(new IllegalStateException("Response size limit exceeded"));
                    return;
                }
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) { delegate.onError(error); }

        @Override
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
