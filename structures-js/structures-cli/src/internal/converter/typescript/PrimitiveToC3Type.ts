import {SpecificTypesConverter} from '../SpecificTypesConverter.js'
import {Type} from 'ts-morph'
import {TypescriptConversionState} from './TypescriptConversionState.js'
import {IConversionContext} from '../IConversionContext.js'
import {BooleanC3Type, C3Type, StringC3Type, IntC3Type, DateC3Type, VoidC3Type} from '@mindignited/continuum-idl'

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
            return new DateC3Type()
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
