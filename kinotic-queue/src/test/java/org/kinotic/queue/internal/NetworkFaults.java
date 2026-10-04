package org.kinotic.queue.internal;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Network faults for one test node, applied to every event bus message the node sends or receives: the node can be
 * cut off, or its messages dropped at random and delayed. The node's Ignite node is untouched, so the cluster keeps
 * counting it as a member, as it does a node behind a failing network.
 */
public final class NetworkFaults {

    private volatile boolean isolated;
    private volatile double dropRate;
    private volatile long maxDelayMillis;

    /**
     * Drops every message the node sends or receives.
     */
    public void isolate() {
        isolated = true;
    }

    /**
     * Drops each message the node receives with the given probability, and delays the others by up to the given time.
     */
    public void degrade(double dropRate, long maxDelayMillis) {
        this.dropRate = dropRate;
        this.maxDelayMillis = maxDelayMillis;
    }

    /**
     * Ends every fault.
     */
    public void heal() {
        isolated = false;
        dropRate = 0;
        maxDelayMillis = 0;
    }

    void install(Vertx vertx) {
        vertx.eventBus().addOutboundInterceptor(delivery -> {
            if (!isolated) {
                delivery.next();
            }
        });
        vertx.eventBus().addInboundInterceptor(delivery -> onInbound(vertx, delivery));
    }

    private void onInbound(Vertx vertx, DeliveryContext<Object> delivery) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (!isolated && random.nextDouble() >= dropRate) {
            long delay = maxDelayMillis > 0 ? random.nextLong(maxDelayMillis + 1) : 0;
            if (delay > 0) {
                vertx.setTimer(delay, t -> delivery.next());
            } else {
                delivery.next();
            }
        }
    }
}
