

package org.kinotic.core.internal.api.service.rpc.converters;

import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.internal.api.service.rpc.RpcResponseConverter;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * Decodes {@code application/octet-stream} responses into the {@code byte[]} or {@link Buffer} the caller
 * declared. The client-side counterpart of {@code BinaryReturnValueConverter}. Resolves the reactive
 * element type (e.g. {@code Flux<byte[]>} to {@code byte[]}) so streaming binary responses decode per element.
 */
@Component
public class BinaryRpcResponseConverter implements RpcResponseConverter {

    private final ReactiveAdapterRegistry reactiveAdapterRegistry;

    public BinaryRpcResponseConverter(ReactiveAdapterRegistry reactiveAdapterRegistry) {
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
    }

    @Override
    public boolean supports(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        String contentType = responseEvent.metadata().get(EventConstants.CONTENT_TYPE_HEADER);
        if (!MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            return false;
        }
        Class<?> valueType = resolveValueType(methodParameter);
        return byte[].class.isAssignableFrom(valueType) || Buffer.class.isAssignableFrom(valueType);
    }

    @Override
    public Object convert(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        byte[] data = responseEvent.data();
        return Buffer.class.isAssignableFrom(resolveValueType(methodParameter)) ? Buffer.buffer(data) : data;
    }

    /**
     * The declared type, or the element type for reactive returns (e.g. {@code Flux<byte[]>} to {@code byte[]}).
     */
    private Class<?> resolveValueType(MethodParameter methodParameter) {
        MethodParameter parameter = methodParameter;
        if (reactiveAdapterRegistry.getAdapter(parameter.getParameterType()) != null) {
            parameter = parameter.nested();
        }
        return parameter.getNestedParameterType();
    }

}
