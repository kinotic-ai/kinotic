import {C3Type, UnionC3Type} from '@mindignited/continuum-idl'
import {ConverterConstants} from '../ConverterConstants.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {
    StatementMapper,
    MultiStatementMapper,
    LiteralStatementMapper
} from './StatementMapper.js'
import {StatementMapperConversionState} from './StatementMapperConversionState.js'
import { camel } from 'radash'

export class UnionC3TypeToStatementMapper implements ITypeConverter<C3Type, StatementMapper, StatementMapperConversionState> {

    convert(value: C3Type, conversionContext: IConversionContext<C3Type, StatementMapper, StatementMapperConversionState>): StatementMapper {
        const unionC3Type = value as UnionC3Type
        const ret: MultiStatementMapper = new MultiStatementMapper(conversionContext.state())

        const state = conversionContext.state()
        const originalJsonPath = conversionContext.currentJsonPath
        const originalPropertyStack = conversionContext.propertyStack
        const originalTargetName = state.targetName
        const originalSourceName = state.sourceName

        const targetJsonPath = originalTargetName + (originalJsonPath.length > 0 ? '.' + originalJsonPath : '')
        const sourceJsonPath = originalSourceName + (originalJsonPath.length > 0 ? '.' + originalJsonPath : '')

        conversionContext.currentJsonPath = ''
        conversionContext.propertyStack = []

        // to map the union type we merge all the properties into a single object
        let counter = 1
        for(let objectC3Type of unionC3Type.types){
            const targetVariableName = camel(targetJsonPath.replaceAll('.', ' ') + `O${counter}`)
            const sourceVariableName = camel(sourceJsonPath.replaceAll('.', ' ') + ` I${counter}`)

            const castStatement
                      = new LiteralStatementMapper(`let ${sourceVariableName} = ${sourceJsonPath} as ${objectC3Type.name}`)

            if(objectC3Type.metadata){
                const sourcePath = objectC3Type.metadata[ConverterConstants.SOURCE_FILE_PATH]
                castStatement.neededImports.push({sourcePath: sourcePath, importName: objectC3Type.name})
            }

            ret.add(castStatement)
            ret.add(new LiteralStatementMapper(`let ${targetVariableName} = ${targetJsonPath}`))

            state.targetName = targetVariableName
            state.sourceName = sourceVariableName

            ret.add(conversionContext.convert(objectC3Type))

            // assign back in case convert had side effects that were lost
            ret.add(new LiteralStatementMapper(`${targetJsonPath} = ${targetVariableName}`))

            counter++
        }

        conversionContext.currentJsonPath = originalJsonPath
        conversionContext.propertyStack = originalPropertyStack
        state.targetName = originalTargetName
        state.sourceName = originalSourceName

        return ret
    }

    supports(value: C3Type, conversionState: StatementMapperConversionState): boolean {
        return value instanceof UnionC3Type || value.type === 'union'
    }
}
