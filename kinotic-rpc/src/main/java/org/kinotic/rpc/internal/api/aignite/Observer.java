

package org.kinotic.rpc.internal.api.aignite;

import io.vertx.core.Handler;

/**
 * Using this as a bridge between tested legacy Ignite logic and the new code base.
 * This will be removed once everything is converted to a {@link reactor.core.publisher.Flux} style architecture
 * Created by Navid Mitchell on 3/28/17.
 */
public interface Observer<T> extends AutoCloseable{


    Observer<T> handler(Handler<T> handler);

    Observer<T> exceptionHandler(Handler<Throwable> handler);

    Observer<T> completionHandler(Handler<Void> handler);

    /**
     * Must be called to start data flowing to the handler
     */
    void start();

}
