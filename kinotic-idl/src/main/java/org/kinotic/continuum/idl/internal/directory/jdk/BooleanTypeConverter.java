

package org.kinotic.continuum.idl.internal.directory.jdk;

import org.kinotic.continuum.idl.api.schema.BooleanC3Type;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.internal.directory.ConversionContext;
import org.kinotic.continuum.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 *
 * Created by navid on 2019-06-17.
 */
@Component
public class BooleanTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {boolean.class, Boolean.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new BooleanC3Type();
    }
}
