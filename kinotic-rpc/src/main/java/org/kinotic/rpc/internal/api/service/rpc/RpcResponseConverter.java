

package org.kinotic.rpc.internal.api.service.rpc;

import org.kinotic.rpc.api.event.Event;
import org.springframework.core.MethodParameter;

/**
 * Converts responses to remote service invocations into a return value for a given proxy calls
 *
 *
 * Created by navid on 2019-04-23.
 */
public interface RpcResponseConverter {

    /**
     * Checks if this converter can convert the incoming response for the given {@link Class}
     * @param responseEvent the response received for the remote service invocation
     * @param methodParameter that is for the return type of the invoked method
     * @return true if this converter can convert the event false if not
     */
    boolean supports(Event<byte[]> responseEvent, MethodParameter methodParameter);

    /**
     * Converts the response {@link Event} into a Java Object to return to the caller
     * @param responseEvent the response received for the remote service invocation
     * @param methodParameter that is for the return type of the invoked method
     * @return the converted value
     */
    Object convert(Event<byte[]> responseEvent, MethodParameter methodParameter);


}
