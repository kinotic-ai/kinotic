import { IConversionContext } from './IConversionContext.js'

/**
 * {@link ITypeConverter} are the base interface for converting a specific type to another specific type.
 * All {@link ITypeConverter}'s should be stateless. Any state that needs to be retained should be stored in the {@link IConversionContext}.
 *
 * @param <BASE_TYPE> the base type to convert from
 * @param <T> the type to convert to
 * @param <S> the state type
 *
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
export interface ITypeConverter<T, R, S> {

    /**
     * Converts the given value to the specified return type.
     * @param value to convert
     * @param conversionContext the context to use for conversion
     * @return the converted type
     */
    convert(value: T,
            conversionContext: IConversionContext<T, R, S>): R

    /**
     * Checks if the given value is supported by this converter
     *
     * @param value to check if supported
     * @param conversionState the state of the conversion, that is contained in the {@link IConversionContext}
     * @return true if this converter can convert the type false if not
     */
    supports(value: T, conversionState: S): boolean

}
