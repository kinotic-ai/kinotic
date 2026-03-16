import {ArrayC3Type, C3Type, ObjectC3Type, PropertyDefinition, UnionC3Type} from '@kinotic/continuum-idl'
import {FunctionDeclaration} from 'ts-morph'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {
    StatementMapper,
    MultiStatementMapper,
    LiteralStatementMapper
} from './StatementMapper.js'
import {StatementMapperConversionState} from './StatementMapperConversionState.js'

export class ObjectC3TypeToStatementMapper implements ITypeConverter<C3Type, StatementMapper, StatementMapperConversionState> {

    convert(value: C3Type, conversionContext: IConversionContext<C3Type, StatementMapper, StatementMapperConversionState>): StatementMapper {
        const state = conversionContext.state()
        const objectC3Type = value as ObjectC3Type
        const ret: MultiStatementMapper = new MultiStatementMapper(state)
        const targetName = state.targetName
        const sourceName = state.sourceName
        const jsonPath = (conversionContext.currentJsonPath.length > 0 ? '.' + conversionContext.currentJsonPath : '')
        const lhs = targetName + jsonPath
        const condition = sourceName + jsonPath

        ret.addLiteral('if ('+condition+') {')
        state.indentMore()
        ret.add(new LiteralStatementMapper(`${lhs} = (${lhs} ? ${lhs} : {})`))

        for(const property of objectC3Type.properties){
            conversionContext.beginProcessingProperty(property.name)

            const functionStatement = this.handleDynamicPropsIfNeeded(conversionContext);

            if(functionStatement){
                ret.add(functionStatement)
                // Since this won't be handled by the normal property conversion we need to remove the source file path
                this.removeSourceFilePath(value)
            }else{
                ret.add(conversionContext.convert(property.type))
            }

            conversionContext.endProcessingProperty()
        }

        state.indentLess()
        ret.addLiteral('}')

        if((value as any)?.metadata?.sourceFilePath){
            delete (value as any).metadata.sourceFilePath
        }

        return ret
    }

    private handleDynamicPropsIfNeeded(conversionContext: IConversionContext<C3Type, StatementMapper, StatementMapperConversionState>): LiteralStatementMapper | null{
        const state = conversionContext.state()
        let ret: LiteralStatementMapper | null = null

        const calculatedProperties = state.getCalculatedProperties(conversionContext.actualJsonPath)
        if(calculatedProperties){
            if(calculatedProperties.length > 1){
                throw new Error(`Only one calculated property is allowed per property. Found ${calculatedProperties.length} for ${conversionContext.actualJsonPath}`)
            }
            const functionDeclaration = state.getUtilFunctionByName(calculatedProperties[0].calculatedPropertyFunctionName)
            if(!functionDeclaration){
                throw new Error(`Could not find function declaration for ${calculatedProperties[0].calculatedPropertyFunctionName}`)
            }
            ret = this.createStatementForFunction(functionDeclaration, conversionContext)
        }

        if(!ret){
            const transformerFunction = state.getTransformFunction(conversionContext.actualJsonPath)
            if(transformerFunction) {
                ret = this.createStatementForFunction(transformerFunction, conversionContext)
            }
        }
        return ret
    }

    private createStatementForFunction(functionDeclaration: FunctionDeclaration,
                                       conversionContext: IConversionContext<C3Type, StatementMapper, StatementMapperConversionState>): LiteralStatementMapper{
        const targetName = conversionContext.state().targetName
        const lhs = targetName + (conversionContext.currentJsonPath.length > 0 ? '.' + conversionContext.currentJsonPath : '')
        const functionName = functionDeclaration.getNameOrThrow('Could not find function name')
        let ret: LiteralStatementMapper = new LiteralStatementMapper(`${lhs} = ${functionName}(entity)`)
        ret.neededImports.push({importName: functionName,
                                sourcePath: functionDeclaration.getSourceFile().getFilePath(),
                                defaultExport: functionDeclaration.isDefaultExport()})
        return ret
    }

    private removeSourceFilePath(value: C3Type): void{
        if(value instanceof ObjectC3Type){
            this.removeSourceFilePathFromObject(value)
        }else if(value instanceof UnionC3Type){
            this.removeSourceFilePathFromUnion(value)
        } else if(value instanceof ArrayC3Type){
            this.removeSourceFilePath((value as ArrayC3Type).contains)
        }
    }

    private removeSourceFilePathFromObject(value: C3Type): void{
        const objectC3Type = value as ObjectC3Type
        if(objectC3Type?.metadata?.sourceFilePath){
            delete objectC3Type.metadata.sourceFilePath
        }
        for(const property of objectC3Type.properties){
            this.removeSourceFilePath(property.type)
        }
    }

    private removeSourceFilePathFromUnion(value: C3Type): void{
        const unionC3Type = value as UnionC3Type
        for(const type of unionC3Type.types){
            this.removeSourceFilePath(type)
        }
    }

    supports(value: C3Type, conversionState: StatementMapperConversionState): boolean {
        return value instanceof ObjectC3Type || value.type === 'object'
    }
}
