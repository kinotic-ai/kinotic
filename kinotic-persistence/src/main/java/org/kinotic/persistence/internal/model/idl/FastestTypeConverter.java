package org.kinotic.persistence.internal.model.idl;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.persistence.api.model.FastestType;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts a {@link FastestType} to an {@link AnyC3Type}: FastestTypeSerializer writes its wrapped data
 * directly, so on the wire it is whatever JSON that data serializes to.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class FastestTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {FastestType.class};

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
