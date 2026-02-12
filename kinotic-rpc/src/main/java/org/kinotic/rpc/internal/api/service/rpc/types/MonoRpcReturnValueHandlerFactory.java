

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
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 *
 * Created by navid on 10/30/19
 */
@Component
public class MonoRpcReturnValueHandlerFactory implements RpcReturnValueHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(MonoRpcReturnValueHandlerFactory.class);

    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;

    public MonoRpcReturnValueHandlerFactory(RpcResponseConverter rpcResponseConverter,
                                            ExceptionConverter exceptionConverter) {
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
    }

    @Override
    public boolean supports(Method method) {
        boolean ret = false;
        if(method.getReturnType().isAssignableFrom(Mono.class)){
            if(GenericTypeResolver.resolveReturnTypeArgument(method, Mono.class) != null){
                ret = true;
            }else{
                log.warn("reactor.core.publisher.Mono is only supported if a generic parameter is provided.\nIf a void return value is desired use Mono<Void> for the method definition.");
            }
        }
        return ret;
    }

    @Override
    public RpcReturnValueHandler createReturnValueHandler(Method method, Object... args) {
        return new MonoRpcReturnValueHandler(new MethodParameter(method, -1),
                                             rpcResponseConverter,
                                             exceptionConverter);
    }
}
