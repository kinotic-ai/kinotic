

package org.kinotic.core.internal.api.service;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.internal.api.service.json.JacksonExceptionConverter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@link ExceptionConverter} of every service proxy and invocation: the Jackson conversion of a service's
 * exception to and from the wire.
 *
 * Created by navid on 2019-04-25.
 */
@Component
public class DefaultExceptionConverter extends ExceptionConverterComposite {

    public DefaultExceptionConverter(KinoticProperties properties, JsonMapper jsonMapper) {
        addConverters(new JacksonExceptionConverter(properties, jsonMapper));
    }

}
