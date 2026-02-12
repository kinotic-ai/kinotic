

package org.kinotic.rpc.internal.api.service.invoker;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link ReturnValueConverter} that delegates to all {@link ReturnValueConverter}'s found in the {@link org.springframework.context.ApplicationContext}
 *
 * Created by navid on 2019-04-25.
 */
@Primary
@Component
public class DefaultReturnValueConverter extends ReturnValueConverterComposite {

    public DefaultReturnValueConverter(List<ReturnValueConverter> converters) {
        addConverters(converters);
    }

}
