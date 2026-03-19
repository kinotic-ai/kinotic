

package org.kinotic.core.internal.api.service.invoker.json;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.api.service.invoker.ReturnValueConverter;
import org.kinotic.core.internal.api.service.json.AbstractJacksonSupport;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;

/**
 * Resolves return values to JSON data using jackson
 * Created by Navid Mitchell on 2019-04-08.
 */
@Component
public class JacksonReturnValueConverter extends AbstractJacksonSupport implements ReturnValueConverter {

    public JacksonReturnValueConverter(JsonMapper jsonMapper,
                                       ReactiveAdapterRegistry reactiveAdapterRegistry,
                                       KinoticProperties kinoticProperties) {
        super(jsonMapper, reactiveAdapterRegistry, kinoticProperties);
    }

    @Override
    public Event<byte[]> convert(Metadata incomingMetadata, Class<?> returnType, Object returnValue) {
        // insure void return types are not mistakenly seen as null
        if(Void.TYPE.isAssignableFrom(returnType)){
            returnValue = Void.TYPE;
        }
        HashMap<String,String> headers = new HashMap<>(1);
        headers.put(EventConstants.CONTENT_TYPE_HEADER, MimeTypeUtils.APPLICATION_JSON_VALUE);

        return createOutgoingEvent(incomingMetadata, headers, returnValue);
    }

    @Override
    public boolean supports(Metadata incomingMetadata, Class<?> returnType) {
        return containsJsonContent(incomingMetadata);
    }

}
