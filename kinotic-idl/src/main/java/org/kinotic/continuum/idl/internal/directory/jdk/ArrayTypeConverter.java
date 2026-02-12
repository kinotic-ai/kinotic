

package org.kinotic.continuum.idl.internal.directory.jdk;

import org.kinotic.continuum.idl.api.schema.ArrayC3Type;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.internal.directory.ConversionContext;
import org.kinotic.continuum.idl.internal.directory.GenericTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts an Array into the correct schema
 * Created by navid on 2019-07-01.
 */
@Component
public class ArrayTypeConverter implements GenericTypeConverter {

    @Override
    public boolean supports(ResolvableType resolvableType) {
        return resolvableType.isArray();
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        ResolvableType componentType = resolvableType.getComponentType();

        return new ArrayC3Type(conversionContext.convert(componentType));
    }
}
