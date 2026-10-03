package org.kinotic.core.api.event;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import reactor.core.Disposable;

/**
 * The cluster-wide event fabric, for a receiver the fabric cannot wire from the bean lifecycle: a bean's
 * {@link org.kinotic.core.api.annotations.Consumer} methods are wired on their own, while an object that is not
 * a bean, such as an Ignite service on the node elected to host it, registers here. Delivery is the fabric's:
 * every event of the type published by any {@link org.kinotic.core.api.annotations.Emitter} anywhere in the
 * cluster, fan-out to every receiver on every node, at most once, on a Vert.x event loop.
 */
public interface EventFabricService {

    /**
     * Registers a receiver of every event of the type.
     *
     * @param eventType the event's concrete class, which identifies the stream
     * @param consumer  the receiver; it must not block
     * @return the registration once the bus consumer behind it is in place, disposed to stop receiving
     * @throws IllegalArgumentException when the type is an interface or abstract
     */
    <T> Future<Disposable> registerConsumer(Class<T> eventType, Handler<T> consumer);

}
