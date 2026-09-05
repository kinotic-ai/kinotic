

package org.kinotic.idl.internal.directory.jdk;

import org.apache.commons.lang3.Validate;
import org.kinotic.idl.api.schema.MapC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 *
 * Created by navid on 2019-07-31
 */
@Component
public class MapTypeConverter implements GenericTypeConverter {

    @Override
    public boolean supports(ResolvableType resolvableType) {
        boolean ret = false;

        ResolvableType collectionResolvableType = resolvableType.as(Map.class);
        if(!collectionResolvableType.equals(ResolvableType.NONE)){
            ret = true;
        }
        return ret;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType, ConversionContext conversionContext) {

        // the key/value types off the Map view, not the declared type: a class implementing Map<K, V>
        // declares no generics of its own, so asking it directly yields NONE
        ResolvableType mapType = resolvableType.as(Map.class);
        ResolvableType keyType = mapType.getGeneric(0);
        Validate.isTrue(!keyType.equals(ResolvableType.NONE), "Map Key type must be resolvable for "+ resolvableType);
        ResolvableType valueType = mapType.getGeneric(1);
        Validate.isTrue(!valueType.equals(ResolvableType.NONE), "Map Value type must be resolvable for "+ resolvableType);

        return new MapC3Type(conversionContext.convert(keyType), conversionContext.convert(valueType));
    }
}
