package org.kinotic.continuum.idl.api.converter;

import org.kinotic.continuum.idl.api.schema.C3Type;

/**
 * {@link IdlConverter} allows for conversion of Continuum IDL types to a specific language type.
 * The {@link IdlConverter} contains state and can be reused but will retain state between requests.
 * If state needs to be reset a new {@link IdlConverter} should be created.
 *
 * @param <R> the type to convert to
 * @param <S> the state type
 *
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
public interface IdlConverter<R, S> {

    /**
     * Converts the given {@link C3Type} to the specific language type.
     * @param type to convert
     * @return the converted type
     */
    R convert(C3Type type);

    /**
     * The {@link C3ConversionContext} that is used during the conversion process.
     * Ths allows {@link C3TypeConverter} to store information needed after the conversion process.
     * @return the {@link C3ConversionContext} used during conversion
     */
    C3ConversionContext<R, S> getConversionContext();

}
