package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Converts a {@link JsonNode} to an {@link AnyC3Type}: it serializes as the JSON it holds, so on the
 * wire it is whatever that is.
 */
@Component
public class JsonNodeToC3Type implements SpecificTypeConverter {

    private static final Class<?>[] supports = {JsonNode.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new AnyC3Type();
    }
}
