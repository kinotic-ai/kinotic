import {C3Type} from '@mindignited/continuum-idl'
import {ts, Type} from 'ts-morph'
import {ITypeConverter} from '../ITypeConverter.js'
import {TypescriptConversionState} from './TypescriptConversionState.js'
import {IConversionContext} from '../IConversionContext.js'
import {QueryOptionsC3Type} from '@mindignited/structures-api'

export class QueryOptionsToC3Type implements ITypeConverter<Type, C3Type, TypescriptConversionState> {

    convert(value: Type<ts.Type>, conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): C3Type {
        return new QueryOptionsC3Type()
    }

    supports(value: Type<ts.Type>, conversionState: TypescriptConversionState): boolean {
        return value.getAliasSymbol()?.getName() === 'QueryOptions'
    }

}
