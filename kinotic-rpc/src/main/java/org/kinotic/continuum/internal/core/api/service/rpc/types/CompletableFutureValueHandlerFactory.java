package org.kinotic.continuum.internal.core.api.service.rpc.types;

import org.kinotic.continuum.internal.core.api.service.ExceptionConverter;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcResponseConverter;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcReturnValueHandler;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcReturnValueHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/24/23.
 */
@Component
public class CompletableFutureValueHandlerFactory implements RpcReturnValueHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureValueHandlerFactory.class);

    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;

    public CompletableFutureValueHandlerFactory(RpcResponseConverter rpcResponseConverter,
                                                ExceptionConverter exceptionConverter) {
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
    }

    @Override
    public boolean supports(Method method) {
        boolean ret = false;
        if(method.getReturnType().isAssignableFrom(CompletableFuture.class)){
            if(GenericTypeResolver.resolveReturnTypeArgument(method, CompletableFuture.class) != null){
                ret = true;
            }else{
                log.warn("CompletableFuture is only supported if a generic parameter is provided.\nIf a void return value is desired use CompletableFuture<Void> for the method definition.");
            }
        }
        return ret;
    }

    @Override
    public RpcReturnValueHandler createReturnValueHandler(Method method, Object... args) {
        return new CompletableFutureValueHandler(new MethodParameter(method, -1),
                                                 rpcResponseConverter,
                                                 exceptionConverter);
    }
}
