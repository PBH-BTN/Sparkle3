package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AfdianMessageClientTest {
    @Test
    void rejectsOversizedResponsesAndCancelsDownload() {
        var body = new AfdianMessageClient.LimitedBody();
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.allocate(4 * 1024 * 1024 + 1)));
        verify(subscription).cancel();
        assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void acceptsSmallResponses() throws Exception {
        var body = new AfdianMessageClient.LimitedBody();
        body.onSubscribe(mock(Flow.Subscription.class));
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3})));
        body.onComplete();
        assertArrayEquals(new byte[]{1, 2, 3}, body.getBody().toCompletableFuture().get());
    }
}
