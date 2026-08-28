package org.kinotic.grindv2.internal.api.services;

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
        return new RunThread(vertx.createVirtualThreadContext(), name, body);
    }

}
