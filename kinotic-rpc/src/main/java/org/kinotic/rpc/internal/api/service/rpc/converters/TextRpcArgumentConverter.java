

package org.kinotic.rpc.internal.api.service.rpc.converters;

import org.kinotic.rpc.internal.api.service.rpc.RpcArgumentConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.lang.reflect.Method;

/**
 *
 * Created by Navid Mitchell on 6/24/20
 */
@Component
public class TextRpcArgumentConverter implements RpcArgumentConverter {

    @Override
    public String producesContentType() {
        return MimeTypeUtils.TEXT_PLAIN_VALUE;
    }

    @Override
    public byte[] convert(Method method, Object[] args) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < args.length; i++){
            if(i > 0){
                sb.append("\n");
            }
            sb.append(args[i]);
        }
        return sb.toString().getBytes();
    }
}
