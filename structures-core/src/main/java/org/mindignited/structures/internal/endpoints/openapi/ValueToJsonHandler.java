package org.mindignited.structures.internal.endpoints.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.mindignited.structures.api.domain.FastestType;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.internal.utils.VertxWebUtil;

import java.util.function.BiFunction;

/**
 * Handles any object that can be serialized directly to JSON
 * Created by Navíd Mitchell 🤪 on 6/1/23.
 */
@RequiredArgsConstructor
class ValueToJsonHandler<T> implements BiFunction<T, Throwable, Void> {

    private final RoutingContext context;
    private final ObjectMapper objectMapper;
    private final boolean send404IfNull;

    public ValueToJsonHandler(RoutingContext context, ObjectMapper objectMapper) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.send404IfNull = false;
    }

    @Override
    public Void apply(T value, Throwable throwable) {
        if (throwable == null) {
            Validate.notNull(context, "context must not be null");
            Object data = (value instanceof FastestType ? ((FastestType) value).data() : value);
            if(data == null && send404IfNull) {
                // No data so send Not Found
                context.response().setStatusCode(404);
                context.response().end();
            }else {
                if (data instanceof RawJson raw) {
                    context.response().putHeader("Content-Type", "application/json");
                    context.response().setStatusCode(200);
                    context.response().end(Buffer.buffer(raw.data()));
                } else {
                    try {
                        context.response().putHeader("Content-Type", "application/json");
                        context.response().setStatusCode(200);
                        byte[] bytes = objectMapper.writeValueAsBytes(value);
                        context.response().end(Buffer.buffer(bytes));
                    } catch (JsonProcessingException e) {
                        VertxWebUtil.writeException(context, e);
                    }
                }
            }
        } else {
            VertxWebUtil.writeException(context, throwable);
        }
        return null;
    }
}
