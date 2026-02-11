package org.kinotic.continuum.idl.internal.directory.jdk;

import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.idl.api.schema.EnumC3Type;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.internal.directory.ConversionContext;
import org.kinotic.continuum.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
@Component
public class EnumTypeConverter implements SpecificTypeConverter {

    @Override
    public Class<?>[] supports() {
        return new Class<?>[] {Enum.class};
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

