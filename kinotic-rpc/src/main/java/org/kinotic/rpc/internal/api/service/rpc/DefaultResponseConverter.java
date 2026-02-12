

package org.kinotic.rpc.internal.api.service.rpc;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link RpcResponseConverter} that delegates to all {@link RpcResponseConverter}'s found in the {@link org.springframework.context.ApplicationContext}
 *
 * Created by Navid Mitchell on 6/23/20
 */
@Primary
@Component
public class DefaultResponseConverter extends RpcResponseConverterComposite {

    public DefaultResponseConverter(List<RpcResponseConverter> converters) {
        addConverters(converters);
    }

}
