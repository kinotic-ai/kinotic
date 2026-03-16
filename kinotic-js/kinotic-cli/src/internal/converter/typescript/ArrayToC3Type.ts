import {ArrayC3Type, C3Type} from '@kinotic/continuum-idl'
import {Type} from 'ts-morph'
import {TypescriptConversionState} from './TypescriptConversionState.js'
import {IConversionContext} from '../IConversionContext.js'
import {ITypeConverter} from '../ITypeConverter.js'

export class ArrayToC3Type implements ITypeConverter<Type, C3Type, TypescriptConversionState> {

    convert(value: Type, conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): C3Type {
        let ret: ArrayC3Type

        const arrayElementType = value?.getArrayElementType()
        if(arrayElementType) {
            ret = new ArrayC3Type(conversionContext.convert(arrayElementType))
        }else{
            throw new Error("Type could not be found for array type "+value.getText())
        }
        return ret
    }

    supports(value: Type, conversionState: TypescriptConversionState): boolean {
        return value.isArray()
    }

}
