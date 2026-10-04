package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts the C3 schema's own types, a {@link C3Type} or a {@link C3Decorator}, to an {@link AnyC3Type}: each
 * serializes as JSON that names its own type, so on the wire it is whatever that is.
 */
@Component
public class C3SchemaToC3Type implements GenericTypeConverter {

    @Override
    public boolean supports(ResolvableType resolvableType) {
        Class<?> rawClass = resolvableType.getRawClass();
        return rawClass != null
                && (C3Type.class.isAssignableFrom(rawClass) || C3Decorator.class.isAssignableFrom(rawClass));
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new AnyC3Type();
    }
}
