

package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.internal.directory.ConversionContext;
import org.kinotic.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 *
 * Created by navid on 2019-06-14.
 */
@Component
public class OptionalTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {Optional.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        ResolvableType genericType = resolvableType.getGeneric(0);
        if(genericType.equals(ResolvableType.NONE)){
            throw new IllegalStateException("Optional found but no generic type defined");
        }

        return conversionContext.convert(genericType);
    }
}
