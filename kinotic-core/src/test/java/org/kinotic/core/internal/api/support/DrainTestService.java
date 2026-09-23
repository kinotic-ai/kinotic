package org.kinotic.core.internal.api.support;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service whose calls stay in flight until the test lets them finish, registered and unregistered by the
 * test itself so the shared context's published services are never touched.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
public interface DrainTestService {

    /**
     * Completes with the value the test opens the gate with.
     */
    Mono<String> awaitGate();

    /**
     * Emits one value and then stays open until the service stops.
     */
    Flux<String> streamUntilStopped();
}
