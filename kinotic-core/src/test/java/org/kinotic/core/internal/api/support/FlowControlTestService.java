package org.kinotic.core.internal.api.support;

import reactor.core.publisher.Flux;

/**
 * Streams whose sources the flow-control tests choose, registered and unregistered by each test itself so the
 * shared context's published services are never touched.
 */
public interface FlowControlTestService {

    /**
     * Emits 0 up to count, exclusive, synchronously on the thread that requests them.
     */
    Flux<Integer> range(int count);

    /**
     * Emits only what the test pushes, only as fast as it is requested, and completes when the test completes it.
     */
    Flux<Integer> controlled();

    /**
     * Emits 0 up to count, exclusive, in one turn of the context it is invoked on, without waiting to be asked,
     * dropping any value its subscriber has not asked for.
     */
    Flux<Integer> burst(int count);

}
