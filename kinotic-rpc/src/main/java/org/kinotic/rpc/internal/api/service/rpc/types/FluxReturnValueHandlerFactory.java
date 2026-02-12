

package org.kinotic.rpc.internal.api.service.rpc.types;

import org.kinotic.rpc.internal.api.service.ExceptionConverter;
import org.kinotic.rpc.internal.api.service.rpc.RpcResponseConverter;
import org.kinotic.rpc.internal.api.service.rpc.RpcReturnValueHandler;
import org.kinotic.rpc.internal.api.service.rpc.RpcReturnValueHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;

/**
 *
 * Created by Navid Mitchell on 5/30/20
 */
@Component
public class FluxReturnValueHandlerFactory implements RpcReturnValueHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(FluxReturnValueHandlerFactory.class);

    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;

    public FluxReturnValueHandlerFactory(RpcResponseConverter rpcResponseConverter,
                                         ExceptionConverter exceptionConverter) {
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
    }

    @Override
    public boolean supports(Method method) {
        boolean ret = false;
        if(method.getReturnType().isAssignableFrom(Flux.class)){
            if(GenericTypeResolver.resolveReturnTypeArgument(method, Flux.class) != null){
                ret = true;
            }else{
                log.warn("reactor.core.publisher.Flux is only supported if a generic parameter is provided.");
            }
        }
        return ret;
    }

    @Override
    public RpcReturnValueHandler createReturnValueHandler(Method method, Object... args) {
        return new FluxReturnValueHandler(new MethodParameter(method, -1),
                                          rpcResponseConverter,
                                          exceptionConverter);
    }

}
