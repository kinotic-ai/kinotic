import { ITypeConverter } from './ITypeConverter.js'
import {Logger} from '../Logger.js'

/**
 * The {@link IConverterStrategy} is used to determine how to convert a specific language type to a Continuum IDL.
 * The {@link IConverterStrategy} should be reusable and thread safe.
 *
 * @param <BASE_TYPE> The base type to convert from
 * @param <T> The type to convert to
 * @param <S> The state type
 *
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
export interface IConverterStrategy<T, R, S> {

    /**
     * An array of {@link ITypeConverter}'s that will be used to convert a specific language type.
     */
    typeConverters(): Array<ITypeConverter<T, R, S>>

    /**
     * The object that will be available via the {@link IConversionContext#state()}.
     * This can be a simple {@link Map} or something with better type safety.
     * This can return a new instance each time it is called.
     * This will be called each time a new {@link IConversionContext} is created.
     * @return the conversion context state.
     */
    initialState(): S | (() => S)

    /**
     * Returns a logger that can be used to log messages.
     */
    logger(): Logger | (() => Logger)

    /**
     * Prints a value to a string.
     * This is used in error messages to help the user understand what value caused the error.
     */
    valueToString(value: T): string

}
