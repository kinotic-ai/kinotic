import {ArrayC3Type, C3Type} from '@mindignited/continuum-idl'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {
    StatementMapper,
    MultiStatementMapper,
} from './StatementMapper.js'
import {StatementMapperConversionState} from './StatementMapperConversionState.js'
import { camel } from 'radash'

export class ArrayC3TypeToStatementMapper implements ITypeConverter<C3Type, StatementMapper, StatementMapperConversionState> {

    convert(value: C3Type, conversionContext: IConversionContext<C3Type, StatementMapper, StatementMapperConversionState>): StatementMapper {
        const arrayC3Type = value as ArrayC3Type

        const ret: MultiStatementMapper = new MultiStatementMapper(conversionContext.state())
        const currentJsonPath = conversionContext.currentJsonPath
        const currentPropertyStack = conversionContext.propertyStack
        const targetName = conversionContext.state().targetName
        const sourceName = conversionContext.state().sourceName
        const targetJsonPath = targetName + (currentJsonPath.length > 0 ? '.' + currentJsonPath : '')
        const sourceJsonPath = sourceName + (currentJsonPath.length > 0 ? '.' + currentJsonPath : '')

        const itemName = camel(sourceJsonPath.replaceAll('.', ' ') + ' Item')
        const internalTargetName = camel(targetJsonPath.replaceAll('.', ' ') + ' Value')

        ret.addLiteral(`if (${sourceJsonPath}) {`);
        conversionContext.state().indentMore()
        ret.addLiteral(`${targetJsonPath} = []`);
        ret.addLiteral(`for (let ${itemName} of ${sourceJsonPath}) {`);
        conversionContext.state().indentMore()
        ret.addLiteral(`let ${internalTargetName}: any`)

        if(arrayC3Type.contains) {
            conversionContext.state().targetName = internalTargetName
            conversionContext.state().sourceName = itemName
            conversionContext.currentJsonPath = ''
            conversionContext.propertyStack = []

            ret.add(conversionContext.convert(arrayC3Type.contains))

            conversionContext.currentJsonPath = currentJsonPath
            conversionContext.propertyStack = currentPropertyStack
            conversionContext.state().targetName = targetName
            conversionContext.state().sourceName = sourceName
        }else{
            throw new Error('ArrayC3Type.contains is undefined')
        }
        ret.addLiteral(`${targetJsonPath}.push(${internalTargetName})`)

        conversionContext.state().indentLess()
        ret.addLiteral('}')

        conversionContext.state().indentLess()
        ret.addLiteral('}')

        return ret
    }

    supports(value: C3Type, conversionState: StatementMapperConversionState): boolean {
        return value instanceof ArrayC3Type || value.type === 'array'
    }
}
