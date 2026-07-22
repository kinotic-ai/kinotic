package org.kinotic.domain.internal.model.idl;

import org.kinotic.domain.api.model.RawJson;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts a {@link RawJson} to an {@link AnyC3Type}: RawJsonSerializer writes its data as a raw JSON value,
 * so on the wire it is whatever JSON it holds.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class RawJsonTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {RawJson.class};

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
