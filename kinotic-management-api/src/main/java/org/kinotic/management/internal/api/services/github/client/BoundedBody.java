package org.kinotic.management.internal.api.services.github.client;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

/**
 * Collects a response body as it streams in, up to a cap: the write that would pass the cap fails, which fails the
 * response before the rest of the body is read, so a body larger than the cap never reaches the heap.
 */
class BoundedBody implements WriteStream<Buffer> {

    private final long maxBytes;
    private final Buffer content = Buffer.buffer();
    private boolean exceeded;

    BoundedBody(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** What arrived, whole when the response succeeded. */
    Buffer content() {
        return content;
    }

    /** Whether the body passed the cap, which is why the response failed. */
    boolean exceeded() {
        return exceeded;
    }

    @Override
    public Future<Void> write(Buffer data) {
        Future<Void> ret;
        if (content.length() + data.length() > maxBytes) {
            exceeded = true;
            ret = Future.failedFuture(new IllegalStateException("The response body exceeds " + maxBytes + " bytes"));
        } else {
            content.appendBuffer(data);
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> end() {
        return Future.succeededFuture();
    }

    @Override
    public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
        return this;
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return false;
    }

    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
        return this;
    }

}
