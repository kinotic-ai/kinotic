

package org.kinotic.core.internal.api.service.rpc;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.internal.api.service.rpc.converters.BinaryRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.converters.JacksonRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.converters.TextRpcResponseConverter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@link RpcResponseConverter} of every service proxy: one converter per content type a reply can carry, JSON,
 * text and binary.
 *
 * Created by Navid Mitchell on 6/23/20
 */
@Component
public class DefaultResponseConverter extends RpcResponseConverterComposite {

    public DefaultResponseConverter(JsonMapper jsonMapper,
                                    ReactiveAdapterRegistry reactiveAdapterRegistry,
                                    KinoticProperties properties) {
        addConverters(new JacksonRpcResponseConverter(jsonMapper, reactiveAdapterRegistry, properties),
                      new TextRpcResponseConverter(),
                      new BinaryRpcResponseConverter(reactiveAdapterRegistry));
    }

}
