package org.kinotic.grind.internal.api.services;

import io.vertx.core.internal.ContextInternal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * One run body executing on its own Vert.x virtual-thread context: the thread may block on
 * task results, while {@code Vertx.currentContext()} - and everything hung from it, such as
 * the platform SecurityContext's participant - resolves inside tasks for the whole body.
 */
public class RunThread {

    private final CompletableFuture<Thread> thread = new CompletableFuture<>();
    private final CountDownLatch done = new CountDownLatch(1);

    RunThread(ContextInternal context, String name, Runnable body) {
        context.runOnContext(v -> {
            Thread current = Thread.currentThread();
            current.setName(name);
            thread.complete(current);
            try {
                body.run();
            } finally {
                done.countDown();
            }
        });
    }

    /**
     * Interrupts the body's thread, delivering cancellation. Effective even when requested
     * before the body has begun: the interrupt lands as soon as the thread exists.
     */
    public void interrupt() {
        thread.thenAccept(Thread::interrupt);
    }

    /**
     * Waits until the body has finished.
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public void join() throws InterruptedException {
        done.await();
    }

}
