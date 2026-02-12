

package org.kinotic.rpc.internal.api.service.invoker;

import org.kinotic.rpc.api.event.Event;

/**
 * Supports resolving arguments from an incoming {@link Event (byte[])}
 *
 * NOTE: All implementations should be thread safe.
 *
 * Created by Navid Mitchell on 2019-03-29.
 */
public interface ArgumentResolver {

    /**
     * Checks if the given {@link Event} is supported by this resolver
     *
     * @param incomingEvent to check if supported
     * @return true if this resolver can resolve arguments false if not
     */
    boolean supports(Event<byte[]> incomingEvent);

    /**
     * Resolves the arguments from the given {@link Event}
     *
     * @param incomingEvent to resolve arguments from
     * @param handlerMethod the {@link HandlerMethod} that will be invoked after the arguments are resolved
     * @return an Object array with all arguments in the order they should be provided to the method to be invoked
     */
    Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod);

}
