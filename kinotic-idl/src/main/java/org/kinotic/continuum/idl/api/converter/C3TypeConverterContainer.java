package org.kinotic.continuum.idl.api.converter;

import org.kinotic.continuum.idl.api.schema.C3Type;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A container for multiple "functional" converters that can be used to convert {@link C3Type}s to a specific type.
 *
 * @param <R> The type to convert to
 * @param <S> The state type
 *
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
public class C3TypeConverterContainer<R, S> implements C3TypeConverter<R, C3Type, S> {

    private final Map<Class<? extends C3Type>,
                      BiFunction<? extends C3Type, C3ConversionContext<R, S>, R>> converterMap = new HashMap<>();

    /**
     * Adds a converter for a specific {@link C3Type} class
     * @param clazz The class of the {@link C3Type} to add a converter for
     * @param converter The converter function
     * @return this {@link C3TypeConverterContainer} for chaining
     * @param <T> The type of {@link C3Type} to add a converter for
     */
    public <T extends C3Type> C3TypeConverterContainer<R, S> addConverter(Class<T> clazz,
                                                                          BiFunction<T, C3ConversionContext<R, S>, R> converter){
        converterMap.put(clazz, converter);
        return this;
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return converterMap.containsKey(c3Type.getClass());
    }

    @Override
    public R convert(C3Type c3Type, C3ConversionContext<R, S> conversionContext) {
        @SuppressWarnings("unchecked")
        BiFunction<C3Type, C3ConversionContext<R, S>, R> converter
                = (BiFunction<C3Type, C3ConversionContext<R, S>, R>) converterMap.get(c3Type.getClass());
        if(converter != null) {
            return converter.apply(c3Type, conversionContext);
        }else{
            throw new IllegalStateException("Type is supported but no converter was found for " + c3Type.getClass().getName());
        }
    }

}

