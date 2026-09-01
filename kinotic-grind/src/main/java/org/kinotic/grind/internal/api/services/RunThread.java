package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import lombok.SneakyThrows;
import org.kinotic.grind.internal.model.RunCancelledException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * One run body executing on its own Vert.x virtual-thread context: the thread may block on
 * task results, while {@code Vertx.currentContext()} - and everything hung from it, such as
 * the platform SecurityContext's participant - resolves inside tasks for the whole body.
 */
public class RunThread {

    // The cancellation future of the run thread executing the calling code; await races the
    // awaited future against it so cancellation wakes a parked await through the future's
    // own resume handshake rather than through the interrupt alone
    private static final ThreadLocal<Future<Void>> CURRENT_CANCELLATION = new ThreadLocal<>();

    private final CompletableFuture<Thread> thread = new CompletableFuture<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private final Promise<Void> cancellation = Promise.promise();

    RunThread(ContextInternal context, String name, Runnable body) {
        context.runOnContext(v -> {
            Thread current = Thread.currentThread();
            current.setName(name);
            thread.complete(current);
            CURRENT_CANCELLATION.set(cancellation.future());
            try {
                body.run();
            } finally {
                CURRENT_CANCELLATION.remove();
                done.countDown();
            }
        });
    }

    /**
     * Interrupts the body's thread, delivering cancellation. Effective even when requested
     * before the body has begun: the interrupt lands as soon as the thread exists.
     */
    public void interrupt() {
        // fail the cancellation future before interrupting: a thread parked in await has
        // suspended its context execution, and only a completion resumes the suspension
        // handshake cleanly - an interrupt alone would unwind the thread and leave the
        // context's task queue owned by a continuation that never runs again
        cancellation.tryFail(new RunCancelledException());
        thread.thenAccept(Thread::interrupt);
    }

    /**
     * Waits until the body has finished.
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public void join() throws InterruptedException {
        done.await();
    }

    /**
     * Awaits the future on the calling run thread, suspending the thread's context execution
     * while parked so queued context tasks - the completions of context-bound futures among
     * them - keep flowing. Returns the result, rethrows the failure cause, or throws
     * {@link RunCancelledException} when the run is cancelled while parked.
     */
    @SneakyThrows
    public static <T> T await(Future<T> future) {
        Promise<T> race = Promise.promise();
        future.onComplete(ar -> {
            if (ar.succeeded()) {
                race.tryComplete(ar.result());
            } else {
                race.tryFail(ar.cause());
            }
        });
        Future<Void> cancellation = CURRENT_CANCELLATION.get();
        if (cancellation != null) {
            cancellation.onComplete(ar -> race.tryFail(new RunCancelledException()));
        }
        try {
            return race.future().await();
        } catch (Throwable t) {
            // an interrupt racing ahead of the cancellation future's resume still surfaces
            // from the park as an unchecked InterruptedException
            throw t instanceof InterruptedException ? new RunCancelledException() : t;
        }
    }

}
