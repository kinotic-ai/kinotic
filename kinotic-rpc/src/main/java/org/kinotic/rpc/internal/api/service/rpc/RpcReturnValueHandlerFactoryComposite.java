

package org.kinotic.rpc.internal.api.service.rpc;

import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Resolves {@link RpcReturnValueHandler}'s by delegating to a list of {@link RpcReturnValueHandlerFactory}'s
 *
 * Created by navid on 2019-04-25.
 */
public class RpcReturnValueHandlerFactoryComposite implements RpcReturnValueHandlerFactory {

    private final List<RpcReturnValueHandlerFactory> factories = new LinkedList<>();

    public RpcReturnValueHandlerFactoryComposite addFactory(RpcReturnValueHandlerFactory factory){
        factories.add(factory);
        return this;
    }

    public RpcReturnValueHandlerFactoryComposite addFactories(RpcReturnValueHandlerFactory... factories){
        if (factories != null) {
            Collections.addAll(this.factories, factories);
        }
        return this;
    }

    public RpcReturnValueHandlerFactoryComposite addFactories(List<? extends RpcReturnValueHandlerFactory> factories){
        this.factories.addAll(factories);
        return this;
    }

    @Override
    public boolean supports(Method method) {
        return selectFactory(method) != null;
    }

    @Override
    public RpcReturnValueHandler createReturnValueHandler(Method method, Object... args) {
        RpcReturnValueHandlerFactory factory = selectFactory(method);
        Assert.notNull(factory, "Unsupported Method no ReturnValueHandlerFactory can be found. Should call supports() first.");
        return factory.createReturnValueHandler(method, args);
    }

    private RpcReturnValueHandlerFactory selectFactory(Method method){
        RpcReturnValueHandlerFactory ret = null;
        for(RpcReturnValueHandlerFactory factory : factories){
            if(factory.supports(method)){
                ret = factory;
                break;
            }
        }
        return ret;
    }

}
