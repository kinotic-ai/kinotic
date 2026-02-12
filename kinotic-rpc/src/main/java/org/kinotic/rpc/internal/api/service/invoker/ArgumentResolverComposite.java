

package org.kinotic.rpc.internal.api.service.invoker;

import org.kinotic.rpc.api.event.Event;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Resolves arguments by delegating to a list of {@link ArgumentResolver}'s
 *
 *
 * Created by Navid Mitchell on 2019-03-29.
 */
public class ArgumentResolverComposite implements ArgumentResolver {

    private final List<ArgumentResolver> resolvers = new LinkedList<>();

    public ArgumentResolverComposite addResolver(ArgumentResolver resolver){
        resolvers.add(resolver);
        return this;
    }

    public ArgumentResolverComposite addResolvers(ArgumentResolver... resolvers){
        if (resolvers != null) {
            Collections.addAll(this.resolvers, resolvers);
        }
        return this;
    }

    public ArgumentResolverComposite addResolvers(List<? extends ArgumentResolver> resolvers){
        this.resolvers.addAll(resolvers);
        return this;
    }

    @Override
    public Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod) {
        ArgumentResolver resolver = selectResolver(incomingEvent);
        Assert.notNull(resolver,"Unsupported Message content no parameter resolver can be found. Should call supports() first.");
        return resolver.resolveArguments(incomingEvent, handlerMethod);
    }

    @Override
    public boolean supports(Event<byte[]> incomingEvent) {
        return selectResolver(incomingEvent) != null;
    }

    private ArgumentResolver selectResolver(Event<byte[]> message){
        ArgumentResolver ret = null;
        for(ArgumentResolver resolver : resolvers){
            if(resolver.supports(message)){
                ret = resolver;
                break;
            }
        }
        return ret;
    }

}
