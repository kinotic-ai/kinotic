

package org.kinotic.continuum.idl.internal.directory.jdk;

import org.kinotic.continuum.idl.api.schema.ArrayC3Type;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.internal.directory.ConversionContext;
import org.kinotic.continuum.idl.internal.directory.GenericTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 *
 * Created by navid on 2019-06-14.
 */
@Component
public class IterableTypeConverter implements GenericTypeConverter {

    @Override
    public boolean supports(ResolvableType resolvableType) {
        boolean ret = false;

        ResolvableType collectionResolvableType = resolvableType.as(Iterable.class);
        if(!collectionResolvableType.equals(ResolvableType.NONE)){
            ret = true;
        }
        return ret;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        ArrayC3Type ret = new ArrayC3Type();

        ResolvableType genericType = resolvableType.getGeneric(0);

        if(!genericType.equals(ResolvableType.NONE)){
            C3Type containsC3Type = conversionContext.convert(genericType);
            ret.setContains(containsC3Type);
        }
        return ret;
    }
}
