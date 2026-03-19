package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.schema.IntC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.internal.directory.ConversionContext;
import org.kinotic.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
@Component
public class IntegerTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {int.class, Integer.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new IntC3Type();
    }

}

