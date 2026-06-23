

package org.kinotic.core.internal.api.service.invoker;

import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.event.EventBusService;
import org.springframework.core.MethodParameter;

/**
 * Converts the return value to a {@link Event} that can bes sent on the {@link EventBusService}
 * NOTE: All implementations should be threadsafe.
 *
 *
 * Created by Navid Mitchell on 2019-03-29.
 */
public interface ReturnValueConverter {

    /**
     * Converts the return value to an {@link Event} to send
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param returnType the {@link MethodParameter} describing the return type of the invoked method
     * @param returnValue that was returned by the invoked method
     * @return the {@link Event} containing the converted data
     */
    Event<byte[]> convert(Metadata incomingMetadata, MethodParameter returnType, Object returnValue);

    /**
     * Checks it a given {@link ReturnValueConverter} supports the incoming {@link Event} by checking the data provided
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param returnType the {@link MethodParameter} describing the return type of the method that will be invoked
     * @return true if this converter can handle the data
     */
    boolean supports(Metadata incomingMetadata, MethodParameter returnType);

}
