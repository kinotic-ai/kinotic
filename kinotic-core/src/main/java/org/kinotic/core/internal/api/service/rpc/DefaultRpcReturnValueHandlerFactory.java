

package org.kinotic.core.internal.api.service.rpc;

import org.kinotic.core.internal.api.service.ExceptionConverter;
import org.kinotic.core.internal.api.service.rpc.types.CompletableFutureValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.FluxReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.MonoRpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.VertxFutureRpcReturnValueHandlerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@link RpcReturnValueHandlerFactory} of every service proxy: one factory per supported return type,
 * {@code CompletableFuture}, Vert.x {@code Future}, {@code Mono} and {@code Flux}.
 *
 * Created by navid on 2019-04-25.
 */
@Component
public class DefaultRpcReturnValueHandlerFactory extends RpcReturnValueHandlerFactoryComposite {

    public DefaultRpcReturnValueHandlerFactory(RpcResponseConverter responseConverter,
                                               ExceptionConverter exceptionConverter) {
        addFactories(new CompletableFutureValueHandlerFactory(responseConverter, exceptionConverter),
                     new VertxFutureRpcReturnValueHandlerFactory(responseConverter, exceptionConverter),
                     new MonoRpcReturnValueHandlerFactory(responseConverter, exceptionConverter),
                     new FluxReturnValueHandlerFactory(responseConverter, exceptionConverter));
    }

}
