package org.kinotic.continuum.idl.api.converter;

import org.kinotic.continuum.idl.api.schema.C3Type;

/**
 * {@link C3TypeConverter} are the base interface for converting {@link C3Type} to a specific type.
 * All {@link C3TypeConverter}'s should be stateless. Any state that needs to be retained should be stored in the {@link C3ConversionContext}.
 *
 * @param <R> the type to convert to
 * @param <T> the {@link C3Type} to convert from
 * @param <S> the state type
 *
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
public interface C3TypeConverter<R, T extends C3Type, S> {

    /**
     * Checks if the given {@link C3Type} is supported by this converter
     *
     * @param c3Type to check if supported
     * @return true if this converter can convert the class false if not
     */
    boolean supports(C3Type c3Type);

    /**
     * Converts the given {@link C3Type} to the specific type.
     * @param c3Type to convert
     * @param conversionContext the context to use for conversion
     * @return the converted type
     */
    R convert(T c3Type,
              C3ConversionContext<R, S> conversionContext);

}
