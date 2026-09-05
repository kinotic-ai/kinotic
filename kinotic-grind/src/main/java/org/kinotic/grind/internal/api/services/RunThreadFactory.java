package org.kinotic.grind.internal.api.services;

import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;

/**
 * Starts run bodies on fresh Vert.x virtual-thread contexts: one context per run and one per
 * parallel child, so context-locals never leak between concurrent executions.
 */
public class RunThreadFactory {

    private final VertxInternal vertx;

    public RunThreadFactory(Vertx vertx) {
        this.vertx = (VertxInternal) vertx;
    }

    /**
     * Starts the given body on a new virtual-thread context.
     * @param name the thread name, for diagnostics
     * @param body the work to run
     * @return the started run thread
     */
    public RunThread start(String name, Runnable body) {
        // The internal createVirtualThreadContext is the only way to get all three properties a
        // run needs at once: unbounded blocking with no blocked-thread checker firing on a task
        // that legitimately takes minutes, a context of its own so Vertx.currentContext() and
        // the context-locals hung off it (the SecurityContext participant) resolve for the whole
        // body, and a working Future.await(). Deploying each run as a virtual-thread verticle
        // was evaluated and rejected: deployment is service lifecycle, the wrong shape for
        // short-lived per-run work
        return new RunThread(vertx.createVirtualThreadContext(), name, body);
    }

}
