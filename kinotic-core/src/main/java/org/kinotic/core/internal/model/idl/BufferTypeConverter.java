package org.kinotic.core.internal.model.idl;

import io.vertx.core.buffer.Buffer;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.ByteC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts a Vert.x {@link Buffer} to an {@link ArrayC3Type} of bytes: BinaryReturnValueConverter sends it
 * straight through as raw bytes on the wire.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class BufferTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {Buffer.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new ArrayC3Type().setContains(new ByteC3Type());
    }
}
