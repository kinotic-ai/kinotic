import {IConverterStrategy} from './IConverterStrategy.js'
import {DefaultConversionContext} from './DefaultConversionContext.js'

/**
 * {@link IConversionContext} allows for conversion of a specific language type to Continuum IDL types.
 * The {@link IConversionContext} contains state and can be reused but will retain state between requests.
 * If state needs to be reset a new {@link IConversionContext} should be created.
 *
 * @param <BASE_TYPE> The base type that will be converted
 * @param <S> The state type
 *s
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
export interface IConversionContext<T, R, S> {

    /**
     * The current json path being processed.
     * This is only updated if beginProcessingProperty and endProcessingProperty are called appropriately.
     * This cannot be updated manually
     */
    actualJsonPath: string

    /**
     * The property stack. This is only updated if beginProcessingProperty and endProcessingProperty are called appropriately.
     * This cannot be updated manually
     */
    actualPropertyStack: string[]

    /**
     * The current json path being processed.
     * This is only updated if beginProcessingProperty and endProcessingProperty are called appropriately.
     * You should not update this manually unless you know what you are doing.
     */
    currentJsonPath: string

    /**
     * The property stack. This is only updated if beginProcessingProperty and endProcessingProperty are called appropriately.
     * You should not update this manually unless you know what you are doing.
     */
    propertyStack: string[]

    /**
     * Should be called before processing a property.
     * This improves debug output
     * @param name of the property
     */
    beginProcessingProperty(name: string): void

    /**
     * Should be called after processing a property.
     */
    endProcessingProperty(): void

    /**
     * Converts the given type to a {@link R} by resolving the proper {@link ITypeConverter}.
     *
     * @param value to convert
     * @return the converted value
     */
    convert(value: T): R

    /**
     * @return the state of this {@link IConversionContext}
     */
    state(): S
}

/**
 * Creates {@link IConversionContext} instances based on a {@link IConverterStrategy}
 * The {@link IConversionContext} allows for conversion of a specific language type to Continuum IDL types.
 * The {@link IConversionContext} contains state and can be reused but will retain state between requests.
 * If state needs to be reset a new {@link IConversionContext} should be created.
 * @param strategy
 */
export function createConversionContext<T, R, S>(strategy: IConverterStrategy<T, R, S>): IConversionContext<T, R, S>{
    return new DefaultConversionContext(strategy)
}
