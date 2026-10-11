package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts a value declared as {@link Object}, which may carry any JSON value, to {@link AnyC3Type}. Only a
 * declaration of exactly {@code Object} converts here; a subclass converts by its own type.
 */
@Component
public class ObjectToC3Type implements SpecificTypeConverter {

    private static final Class<?>[] supports = {Object.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType, ConversionContext conversionContext) {
        return new AnyC3Type();
    }
}
