

package org.kinotic.rpc.internal.api.service.rpc.converters;

import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.EventConstants;
import org.kinotic.rpc.internal.api.service.rpc.RpcResponseConverter;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 *
 * Created by Navid Mitchell on 6/23/20
 */
@Component
public class TextRpcResponseConverter implements RpcResponseConverter {

    @Override
    public boolean supports(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        boolean ret = false;
        String contentType = responseEvent.metadata().get(EventConstants.CONTENT_TYPE_HEADER);
        if(contentType != null && !contentType.isEmpty()){
            ret = MimeTypeUtils.TEXT_PLAIN_VALUE.contentEquals(contentType)
                    ||
                    (contentType.equalsIgnoreCase("application/text")
                        && methodParameter.getNestedParameterType().isAssignableFrom(String.class));
        }
        return ret;
    }

    @Override
    public Object convert(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        return new String(responseEvent.data());
    }

}
