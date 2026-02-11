import {ITypeConverter} from './ITypeConverter.js'
import {IConversionContext} from './IConversionContext.js'

/**
 * {@link ITypeConverter} are the base interface for converting a specific type to another specific type.
 */
export abstract class SpecificTypesConverter<T, R, S, MATCH_VALUE> implements ITypeConverter<T, R, S> {

    private readonly matchValueExtractor: (arg: T) => MATCH_VALUE
    private typeConverterFunctions: Map<MATCH_VALUE, ((type: T, context: IConversionContext<T, R, S>) => R)>


    protected constructor(matchValueExtractor: (arg: T) => MATCH_VALUE,
                          typeConverterFunctions: Map<MATCH_VALUE, ((type: T, context: IConversionContext<T, R, S>) => R)>) {
        this.matchValueExtractor = matchValueExtractor
        this.typeConverterFunctions = typeConverterFunctions
    }

    convert(value: T, conversionContext: IConversionContext<T, R, S>): R {
        const matchValue: MATCH_VALUE = this.matchValueExtractor(value)
        const converterFunction = this.typeConverterFunctions.get(matchValue)
        if(!converterFunction){
            // Should never happen due to supports call!
            throw new Error("Converter function could not be resolved, WAT!")
        }
        return converterFunction(value, conversionContext)
    }

    supports(value: T): boolean {
        const matchValue: MATCH_VALUE = this.matchValueExtractor(value)
        return this.typeConverterFunctions.has(matchValue)
    }

}
