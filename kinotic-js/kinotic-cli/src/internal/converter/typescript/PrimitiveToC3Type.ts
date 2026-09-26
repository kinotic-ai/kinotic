import {SpecificTypesConverter} from '@/internal/converter/SpecificTypesConverter'
import {Type} from 'ts-morph'
import {TypescriptConversionState} from './TypescriptConversionState'
import {IConversionContext} from '@/internal/converter/IConversionContext'
import {BooleanC3Type, C3Type, StringC3Type, IntC3Type, VoidC3Type} from '@kinotic-ai/idl'

/**
 * Converts typescript primitive types to C3Types
 */
export class PrimitiveToC3Type extends SpecificTypesConverter<Type, C3Type, TypescriptConversionState, string>{

    constructor() {
        const map: Map<string, (type: Type, context: IConversionContext<Type, C3Type, TypescriptConversionState>) => C3Type> = new Map()
        map.set('string', () => {
            return new StringC3Type()
        })

        map.set('boolean', () => {
            return new BooleanC3Type()
        })

        map.set('number', () => {
            return new IntC3Type()
        })

        map.set('date', () => {
            throw new Error('The Date type is not supported because values travel as JSON, where a date is an ISO-8601 string. '
                            + 'Use a string instead, decorated with @DateTime when it is an entity property')
        })

        map.set('void', () => {
            return new VoidC3Type()
        })

        super((arg: Type) => {
            if(arg.isLiteral()){
                return arg.getApparentType().getText().toLowerCase()
            }else{
                return arg.getText().toLowerCase()
            }
        }, map)
    }
}
