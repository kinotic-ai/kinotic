package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import org.kinotic.grind.internal.model.RunCancelledException;

import java.util.concurrent.CountDownLatch;

/**
 * One run body executing on its own Vert.x virtual-thread context: the thread may block on
 * task results, while {@code Vertx.currentContext()} - and everything hung from it, such as
 * the platform SecurityContext's participant - resolves inside tasks for the whole body.
 */
public class RunThread {

    // The cancellation of the run executing on the calling thread, for the body's duration
    private static final ThreadLocal<Future<Void>> CANCELLATION = new ThreadLocal<>();

    private final Promise<Thread> thread = Promise.promise();
    private final Promise<Void> cancellation = Promise.promise();
    private final CountDownLatch done = new CountDownLatch(1);

    RunThread(ContextInternal context, String name, Runnable body) {
        context.runOnContext(v -> {
            Thread current = Thread.currentThread();
            current.setName(name);
            thread.complete(current);
            CANCELLATION.set(cancellation.future());
            try {
                body.run();
            } finally {
                CANCELLATION.remove();
                done.countDown();
            }
        });
    }

    /**
     * Awaits the given future on the calling run thread, parking the thread until the future
     * completes or the run is cancelled.
     * @param future to await
     * @param <T> the awaited type
     * @return the future's value
     * @throws RunCancelledException when the run is cancelled while waiting
     */
    public static <T> T await(Future<T> future) {
        // the race promise is deliberately unbound: a bound one would queue its own listener
        // dispatch on the run's context, behind the body that is waiting on it
        Promise<T> race = Promise.promise();
        future.onComplete((value, error) -> {
            if (error != null) {
                race.tryFail(error);
            } else {
                race.tryComplete(value);
            }
        });
        CANCELLATION.get().onComplete((value, error) -> race.tryFail(new RunCancelledException()));
        T ret;
        try {
            ret = race.future().await();
        } catch (Throwable t) {
            // the interrupt can land after the cancellation already resumed the continuation,
            // and Future.await rethrows it; the run is cancelled either way
            if (t instanceof InterruptedException) {
                throw new RunCancelledException();
            }
            throw t;
        }
        return ret;
    }

    /**
     * Interrupts the body's thread, delivering cancellation. Effective even when requested
     * before the body has begun: the interrupt lands as soon as the thread exists.
     */
    public void interrupt() {
        // Cancelling before the interrupt is what keeps the context's task queue usable: a
        // body parked in await() wakes through the future's resume handshake, which hands
        // queue ownership back. A raw interrupt leaves the continuation suspended forever,
        // and the queue - along with every task completion dispatched onto it - with it
        cancellation.tryFail(new RunCancelledException());
        thread.future().onSuccess(Thread::interrupt);
    }

    /**
     * Waits until the body has finished.
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public void join() throws InterruptedException {
        done.await();
    }

}
