package org.kinotic.idl.internal.directory.jdk;

import org.apache.commons.lang3.Validate;
import org.kinotic.idl.api.schema.EnumC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
@Component
public class EnumTypeConverter implements GenericTypeConverter {

    @Override
    public boolean supports(ResolvableType resolvableType) {
        // a predicate, not an exact-class match: a concrete enum's raw class is never java.lang.Enum,
        // and this must claim enums before the PojoTypeConverter catch-all introspects them as beans
        Class<?> rawClass = resolvableType.getRawClass();
        return rawClass != null && rawClass.isEnum();
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        Class<?> rawClass = resolvableType.getRawClass();
        Assert.notNull(rawClass, "Raw class could not be found for ResolvableType");

        EnumC3Type ret = new EnumC3Type();
        ret.setNamespace(rawClass.getPackage().getName());
        ret.setName(rawClass.getSimpleName());

        @SuppressWarnings("unchecked")
        Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) resolvableType.resolve();

        Validate.notNull(enumType, "Enum type cannot be resolved");

        for (Enum<?> enumConstant : enumType.getEnumConstants()) {
            ret.addValue(enumConstant.name());
        }

        return ret;
    }

}

