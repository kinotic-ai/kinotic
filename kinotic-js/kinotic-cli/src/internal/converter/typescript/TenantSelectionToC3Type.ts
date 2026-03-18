import {C3Type} from '@kinotic-ai/idl'
import {ts, Type} from 'ts-morph'
import {ITypeConverter} from '@/internal/converter/ITypeConverter'
import {TypescriptConversionState} from './TypescriptConversionState.js'
import {IConversionContext} from '@/internal/converter/IConversionContext'
import {TenantSelectionC3Type} from '@kinotic-ai/os-api'

export class TenantSelectionToC3Type implements ITypeConverter<Type, C3Type, TypescriptConversionState> {

    convert(value: Type<ts.Type>, conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): C3Type {
        return new TenantSelectionC3Type()
    }

    supports(value: Type<ts.Type>, conversionState: TypescriptConversionState): boolean {
        return value.getAliasSymbol()?.getName() === 'TenantSelection'
    }

}
