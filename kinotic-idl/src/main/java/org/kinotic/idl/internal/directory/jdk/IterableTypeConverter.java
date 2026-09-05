

package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;
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

        // the element type off the Iterable view, not the declared type: a class implementing Iterable<T>
        // declares no generic of its own, so asking it directly yields NONE and a headless ArrayC3Type
        ResolvableType genericType = resolvableType.as(Iterable.class).getGeneric(0);

        if(!genericType.equals(ResolvableType.NONE)){
            C3Type containsC3Type = conversionContext.convert(genericType);
            ret.setContains(containsC3Type);
        }
        return ret;
    }
}
