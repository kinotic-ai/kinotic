package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.util.TokenBuffer;

/**
 * Converts a {@link TokenBuffer} to an {@link AnyC3Type}: it serializes by replaying its buffered tokens,
 * so on the wire it is whatever JSON it holds.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class TokenBufferTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {TokenBuffer.class};

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
