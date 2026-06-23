

package org.kinotic.core.internal.api.service.invoker;

import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.utils.EventUtil;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.Map;

/**
 * Sends {@code byte[]} and {@link Buffer} return values straight through as
 * {@code application/octet-stream}, skipping JSON serialization. For reactive returns the element
 * type decides, so a {@code Flux<byte[]>} streams raw bytes per element.
 */
@Component
// Must be selected ahead of JacksonReturnValueConverter, which otherwise claims any return on a JSON request.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RawReturnValueConverter implements ReturnValueConverter {

    private final ReactiveAdapterRegistry reactiveAdapterRegistry;

    public RawReturnValueConverter(ReactiveAdapterRegistry reactiveAdapterRegistry) {
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
    }

    @Override
    public boolean supports(Metadata incomingMetadata, MethodParameter returnType) {
        Class<?> valueType = resolveValueType(returnType);
        return byte[].class.isAssignableFrom(valueType) || Buffer.class.isAssignableFrom(valueType);
    }

    @Override
    public Event<byte[]> convert(Metadata incomingMetadata, MethodParameter returnType, Object returnValue) {
        byte[] bytes = returnValue instanceof Buffer buffer ? buffer.getBytes() : (byte[]) returnValue;
        return EventUtil.createReplyEvent(incomingMetadata,
                                          Map.of(EventConstants.CONTENT_TYPE_HEADER, MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE),
                                          () -> bytes);
    }

    /**
     * The declared return type, or the element type for reactive returns (e.g. {@code Flux<byte[]>} to {@code byte[]}).
     */
    private Class<?> resolveValueType(MethodParameter returnType) {
        Class<?> declaredType = returnType.getParameterType();
        if (reactiveAdapterRegistry.getAdapter(declaredType) != null) {
            Class<?> elementType = GenericTypeResolver.resolveReturnTypeArgument(returnType.getMethod(), declaredType);
            if (elementType != null) {
                return elementType;
            }
        }
        return declaredType;
    }

}
