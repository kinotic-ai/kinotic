

package org.kinotic.rpc.internal.api.service.invoker;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link ArgumentResolver} that delegates to all {@link ArgumentResolver}'s found in the {@link org.springframework.context.ApplicationContext}
 *
 * Created by navid on 2019-04-25.
 */
@Primary
@Component
public class DefaultArgumentResolver extends ArgumentResolverComposite {

    public DefaultArgumentResolver(List<ArgumentResolver> argumentResolvers) {
        addResolvers(argumentResolvers);
    }

}
