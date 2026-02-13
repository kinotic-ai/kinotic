package org.kinotic.persistence.internal.endpoints.openapi;

import io.vertx.core.Completable;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.persistence.api.domain.FastestType;
import org.kinotic.core.api.domain.RawJson;
import org.kinotic.persistence.internal.utils.VertxWebUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles any object that can be serialized directly to JSON
 * Created by Navíd Mitchell 🤪 on 6/1/23.
 */
@RequiredArgsConstructor
class ValueToJsonHandler<T> implements Completable<T> {

    private final RoutingContext context;
    private final ObjectMapper objectMapper;
    private final boolean send404IfNull;

    public ValueToJsonHandler(RoutingContext context, ObjectMapper objectMapper) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.send404IfNull = false;
    }

    @Override
    public void complete(T value, Throwable throwable) {
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
                    } catch (JacksonException e) {
                        VertxWebUtil.writeException(context, e);
                    }
                }
            }
        } else {
            VertxWebUtil.writeException(context, throwable);
        }
    }
}
