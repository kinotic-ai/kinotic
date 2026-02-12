package org.kinotic.persistence.internal.idl.converters.elastic;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.converter.Cacheable;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.UnionC3Type;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO: should root / entity objects be allowed to be a union type.
 * Created by Navíd Mitchell 🤪 on 5/26/23.
 */
public class UnionC3TypeToElastic implements C3TypeConverter<Property, UnionC3Type, ElasticConversionState>, Cacheable {

    @Override
    public Property convert(UnionC3Type unionType,
                            C3ConversionContext<Property, ElasticConversionState> conversionContext) {
        // For elastic, we just merge all the fields into one object and map that
        ObjectC3Type merged = new ObjectC3Type();
        merged.setName(unionType.getName());
        merged.setNamespace(unionType.getNamespace());

        // We also keep the data in a map so, we can check for duplicate fields
        Map<String, PropertyDefinition> mergedFields = new HashMap<>();

        for (ObjectC3Type c3Type : unionType.getTypes()) {
            for(PropertyDefinition property: c3Type.getProperties()) {

                String fieldName = property.getName();
                C3Type type = property.getType();

                PropertyDefinition existingProperty = mergedFields.get(fieldName);
                if(existingProperty != null){
                    // TODO: what to do about decorators, make sure match merge them ? or throw an error (Ask Nick)
                    if(!existingProperty.getType().equals(type)){
                        throw new IllegalArgumentException("Field '" + fieldName + "' is defined in multiple types in the union '" + unionType.getName() + "' with different types");
                    }
                }else {
                    merged.addProperty(property);
                    mergedFields.put(property.getName(), property);
                }
            }
        }
        return conversionContext.convert(merged);
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof UnionC3Type;
    }

}
