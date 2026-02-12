

package org.kinotic.rpc.internal.api.service.json;

import org.kinotic.rpc.api.config.KinoticRpcProperties;
import org.kinotic.rpc.api.exceptions.RpcInvocationException;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.EventConstants;
import org.kinotic.rpc.api.event.Metadata;
import org.kinotic.rpc.api.service.ServiceExceptionWrapper;
import org.kinotic.rpc.internal.api.service.ExceptionConverter;
import org.kinotic.rpc.internal.utils.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.EncodingException;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by Navid Mitchell on 2019-04-08.
 */
@Component
@Order
public class JacksonExceptionConverter implements ExceptionConverter {

    private static final Logger log = LoggerFactory.getLogger(JacksonExceptionConverter.class);

    private final JsonMapper jsonMapper;
    private final KinoticRpcProperties properties;

    public JacksonExceptionConverter(KinoticRpcProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Event<byte[]> convert(Metadata incomingMetadata, Throwable throwable) {
        Map<String, String> headers = new HashMap<>(2);

        headers.put(EventConstants.ERROR_HEADER, throwable.getMessage());
        headers.put(EventConstants.CONTENT_TYPE_HEADER, MimeTypeUtils.APPLICATION_JSON_VALUE);

        return EventUtil.createReplyEvent(incomingMetadata, headers, () -> {
            Class<? extends Throwable> clazz = throwable.getClass();
            ServiceExceptionWrapper wrapper = new ServiceExceptionWrapper(clazz.getSimpleName(),
                                                                          clazz.getName(),
                                                                          throwable.getMessage());

            if(properties.isDebug()) {
                wrapper.setStackTrace(throwable.getStackTrace());
            }
            try {
                return jsonMapper.writeValueAsBytes(wrapper);
            } catch (JacksonException e) {
                throw new EncodingException("JSON encoding error: " + e.getOriginalMessage(), e);
            }
        });
    }

    @Override
    public Throwable convert(Event<byte[]> event) {
        Throwable ret = null;
        if(MimeTypeUtils.APPLICATION_JSON_VALUE.equals(event.metadata().get(EventConstants.CONTENT_TYPE_HEADER))){

            ServiceExceptionWrapper wrapper = null;

            try {
                wrapper = jsonMapper.readValue(event.data(), ServiceExceptionWrapper.class);
            } catch (JacksonException e) {
                log.error("Could not deserialize ServiceExceptionWrapper from json", e);
                ret = new RpcInvocationException(event.metadata().get(EventConstants.ERROR_HEADER));
            }

            if(wrapper != null) {
                try {
                    ret = (Throwable) Class.forName(wrapper.getExceptionClass())
                                           .getDeclaredConstructor(String.class)
                                           .newInstance(wrapper.getErrorMessage());

                    if (wrapper.getStackTrace() != null && wrapper.getStackTrace().length > 0) {
                        ret.setStackTrace(wrapper.getStackTrace());
                    }

                } catch (Exception e) {
                    ret = new RpcInvocationException(event.metadata().get(EventConstants.ERROR_HEADER))
                            .setOriginalClassName(wrapper.getExceptionClass())
                            .setOriginalStackTrace(wrapper.getStackTrace());

                }
            }
        }else{
            ret = new RpcInvocationException(event.metadata().get(EventConstants.ERROR_HEADER));
        }
        return ret;
    }

    @Override
    public boolean supports(Metadata incomingMetadata) {
        return true;
    }

}
