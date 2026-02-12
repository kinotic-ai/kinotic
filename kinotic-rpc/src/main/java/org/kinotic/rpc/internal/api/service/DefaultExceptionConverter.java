

package org.kinotic.rpc.internal.api.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link ExceptionConverter} that delegates to all {@link ExceptionConverter}'s found in the {@link org.springframework.context.ApplicationContext}
 *
 * Created by navid on 2019-04-25.
 */
@Primary
@Component
public class DefaultExceptionConverter extends ExceptionConverterComposite {

    public DefaultExceptionConverter(List<ExceptionConverter> exceptionConverters) {
        addConverters(exceptionConverters);
    }

}
