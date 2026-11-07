

package org.kinotic.core.internal.api.service.rpc;

import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Default {@link RpcReturnValueHandlerFactory} that delegates to all {@link RpcReturnValueHandlerFactory}'s found in the {@link org.springframework.context.ApplicationContext}
 *
 * Created by navid on 2019-04-25.
 */
@Primary
public class DefaultRpcReturnValueHandlerFactory extends RpcReturnValueHandlerFactoryComposite {

    public DefaultRpcReturnValueHandlerFactory(List<RpcReturnValueHandlerFactory> factories) {
        addFactories(factories);
    }

}
