package com.ybc.ybc.global.monitoring.incident;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Limits bytes while receiving, keeping HttpRequest.timeout active until the entire body is consumed. */
final class IncidentResponseBody implements HttpResponse.BodySubscriber<byte[]> {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    static HttpResponse.BodyHandler<byte[]> handler() { return info -> new IncidentResponseBody(); }

    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        long incoming = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
        if (bytes.size() + incoming > IncidentJson.MAX_FILE_BYTES) {
            subscription.cancel();
            result.completeExceptionally(new IOException("외부 API 응답이 너무 큽니다."));
            return;
        }
        for (ByteBuffer buffer : buffers) {
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable error) { result.completeExceptionally(error); }
    @Override public void onComplete() { result.complete(bytes.toByteArray()); }
}
