import {C3Type} from '@kinotic/continuum-idl'
import {ts, Type} from 'ts-morph'
import {ITypeConverter} from '../ITypeConverter.js'
import {TypescriptConversionState} from './TypescriptConversionState.js'
import {IConversionContext} from '../IConversionContext.js'
import {TenantSelectionC3Type} from '@kinotic/structures-api'

export class TenantSelectionToC3Type implements ITypeConverter<Type, C3Type, TypescriptConversionState> {

    convert(value: Type<ts.Type>, conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): C3Type {
        return new TenantSelectionC3Type()
    }

    supports(value: Type<ts.Type>, conversionState: TypescriptConversionState): boolean {
        return value.getAliasSymbol()?.getName() === 'TenantSelection'
    }

}
