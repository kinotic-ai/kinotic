import {C3Type, FunctionDefinition, ObjectC3Type} from '@kinotic-ai/idl'
import fs from 'fs'
import {Liquid} from 'liquidjs'
import path from 'path'
import {Project, Type} from 'ts-morph'
import {fileURLToPath} from 'url'
import {PageableC3Type, PageC3Type} from '@kinotic-ai/os-api'
import {EntitiesPathConfig, KinoticProjectConfig} from '@kinotic-ai/core'
import {createImportString, StatementMapper} from './converter/codegen/StatementMapper'
import {StatementMapperConversionState} from './converter/codegen/StatementMapperConversionState'
import {StatementMapperConverterStrategy} from './converter/codegen/StatementMapperConverterStrategy'
import {createConversionContext, IConversionContext} from './converter/IConversionContext'
import {tsDecoratorToC3Decorator} from './converter/typescript/ConverterUtils'
import {TypescriptConversionState} from './converter/typescript/TypescriptConversionState'
import {TypescriptConverterStrategy} from './converter/typescript/TypescriptConverterStrategy'
import {Logger} from './Logger'
import {
    ConversionConfiguration, convertAllEntities,
    createTsMorphProject,
    EntityInfo,
    GeneratedServiceInfo,
    getRelativeImportPath,
    tryGetNodeModuleName, writeEntityJsonToFilesystem, writeGeneratedServiceInfoToFilesystem
} from './Utils'
import chalk from 'chalk'

export type GeneratedEntityProcessor = (entityInfo: EntityInfo, serviceInfo: GeneratedServiceInfo[]) => Promise<void>


/**
 * Helper service for generating code.s
 */
export class EntityCodeGenerationService {

    private readonly fileExtensionForImports: string
    private readonly logger: Logger
    private readonly engine: Liquid
    private readonly tsMorphProject: Project
    private readonly conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>

    constructor(application: string,
                fileExtensionForImports: string,
                logger: Logger){
        this.fileExtensionForImports = fileExtensionForImports
        this.logger = logger
        this.engine = new Liquid({
                                     root: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../templates/'),  // root for templates lookup
                                     extname: '.liquid'
                                 });
        this.tsMorphProject = createTsMorphProject()

        const state = new TypescriptConversionState(application)
        state.shouldAddSourcePathToMetadata = false
        this.conversionContext = createConversionContext(new TypescriptConverterStrategy(state, logger))
    }

    public async generateAllEntities(projectConfig: KinoticProjectConfig,
                                     verbose: boolean,
                                     entityProcessor?: GeneratedEntityProcessor){

        for(const entitiesPathEntry of projectConfig.entitiesPaths) {
            const resolvedPathConfig = this.resolveEntitiesPathConfig(entitiesPathEntry, projectConfig)
            const config: ConversionConfiguration = {
                application        : projectConfig.application,
                entitiesPath       : resolvedPathConfig.path,
                verbose            : verbose,
                logger             : this.logger
            }
            await this.processEntities(config, projectConfig, resolvedPathConfig, entityProcessor)
        }

    }

    /**
     * Normalizes an entitiesPaths entry into a fully resolved {@link EntitiesPathConfig}.
     */
    private resolveEntitiesPathConfig(entry: string | EntitiesPathConfig,
                                      projectConfig: KinoticProjectConfig): EntitiesPathConfig {
        if (typeof entry === 'string') {
            if (!projectConfig.generatedPath) {
                throw new Error(`entitiesPaths contains a plain string "${entry}" but no generatedPath is configured. `
                    + `Either use an EntitiesPathConfig object or set generatedPath.`)
            }
            return {
                path: entry,
                repositoryPath: projectConfig.generatedPath,
                mirrorFolderStructure: false
            }
        }
        return {
            ...entry,
            mirrorFolderStructure: entry.mirrorFolderStructure ?? true
        }
    }

    private async processEntities(config: ConversionConfiguration,
                                  projectConfig: KinoticProjectConfig,
                                  pathConfig: EntitiesPathConfig,
                                  entityProcessor?: GeneratedEntityProcessor): Promise<void>{

        if (!fs.existsSync(config.entitiesPath)) {
            throw new Error(`Entities path does not exist: ${config.entitiesPath}`)
        }

        const convertedEntities: EntityInfo[] = convertAllEntities(config)

        if (convertedEntities.length > 0) {

            for (const entityInfo of convertedEntities) {

                this.logger.logVerbose(`Generated Persistence Mapping for ${entityInfo.entity.namespace}.${entityInfo.entity.name}`, config.verbose)

                const repositoryOutputPath = this.resolveRepositoryOutputPath(entityInfo, pathConfig)

                const generatedServices: GeneratedServiceInfo[] = []

                generatedServices.push(await this.generateRepository(false,
                                                                     entityInfo,
                                                                     projectConfig,
                                                                     repositoryOutputPath))

                if(entityInfo.multiTenantSelectionEnabled){
                    generatedServices.push(await this.generateRepository(true,
                                                                         entityInfo,
                                                                         projectConfig,
                                                                         repositoryOutputPath))
                }

                if(config.verbose){

                    await writeEntityJsonToFilesystem(repositoryOutputPath,
                                                      entityInfo.entity,
                                                      this.logger)

                    for(let generatedServiceInfo of generatedServices) {
                        if (generatedServiceInfo.namedQueries.length > 0) {
                            await writeGeneratedServiceInfoToFilesystem(repositoryOutputPath,
                                                                        generatedServiceInfo,
                                                                        this.logger)
                        }
                    }
                }

                if(entityProcessor){
                    await entityProcessor(entityInfo, generatedServices)
                }
            }

        } else {
            this.logger.logVerbose(`No Entities found For namespace: ${config.application} and Entities Path: ${config.entitiesPath}`, config.verbose)
        }
    }

    /**
     * Resolves the output path for generated repository files based on the entity's source location
     * and the path configuration.
     */
    private resolveRepositoryOutputPath(entityInfo: EntityInfo, pathConfig: EntitiesPathConfig): string {
        const absEntitiesPath = path.resolve(pathConfig.path)
        const absRepositoryPath = path.resolve(pathConfig.repositoryPath)

        if (pathConfig.mirrorFolderStructure) {
            // Compute the entity's relative directory within the entities path
            const absEntityDir = path.dirname(path.resolve(entityInfo.exportedFromFile))
            const relativeDir = path.relative(absEntitiesPath, absEntityDir)
            return path.resolve(absRepositoryPath, relativeDir)
        }

        return absRepositoryPath
    }

    private async generateRepository(adminService: boolean,
                                      entityInfo: EntityInfo,
                                      projectConfig: KinoticProjectConfig,
                                      repositoryOutputPath: string): Promise<GeneratedServiceInfo> {

        const adminPrefix = (adminService ? 'Admin' : '')
        const fileExtensionForImports = this.fileExtensionForImports
        const baseEntityServicePath = path.resolve(repositoryOutputPath, 'generated', `Base${entityInfo.entity.name}${adminPrefix}Repository.ts`)
        const entityServicePath = path.resolve(repositoryOutputPath, `${entityInfo.entity.name}${adminPrefix}Repository.ts`)

        const entityName = entityInfo.entity.name
        const entityNamespace = entityInfo.entity.namespace
        const defaultExport = entityInfo.defaultExport
        const validate = projectConfig.validate

        let entityImportPath = tryGetNodeModuleName(entityInfo.exportedFromFile)
        if(!entityImportPath){
            entityImportPath = getRelativeImportPath(baseEntityServicePath, entityInfo.exportedFromFile, fileExtensionForImports)
        }

        // We don't add validation logic to admin services since there are no save/update methods there
        let statement: StatementMapper | null = null
        let validationLogic: string | null = null
        if(!adminService) {
            statement = this.createStatementMapper(entityInfo)
            validationLogic = statement.toStatementString()
        }

        const importStatements = createImportString(statement, baseEntityServicePath, fileExtensionForImports) || ''

        //  We always generate the base entity service. This way if our internal logic changes we can update it
        fs.mkdirSync(path.dirname(baseEntityServicePath), {recursive: true})
        const baseReadStream= await this.engine.renderFileToNodeStream(`Base${adminPrefix}Repository`,
                                                                       {
                                                                           entityName,
                                                                           entityNamespace,
                                                                           defaultExport,
                                                                           entityImportPath,
                                                                           validationLogic,
                                                                           importStatements
                                                                       })
        let baseWriteStream = fs.createWriteStream(baseEntityServicePath)
        baseReadStream.pipe(baseWriteStream)

        //  we only generate if the file does not exist
        let namedQueries: FunctionDefinition[] = []
        if (!fs.existsSync(entityServicePath)) {
            const readStream= await this.engine.renderFileToNodeStream(`${adminPrefix}Repository`,
                                                                       {
                                                                           entityName,
                                                                           entityNamespace,
                                                                           validate,
                                                                           fileExtensionForImports
                                                                       })
            let writeStream = fs.createWriteStream(entityServicePath)
            readStream.pipe(writeStream)
        } else {
            // if it already exists we check if there are any named queries defined
            namedQueries = await this.processNamedQueries(`${entityName}${adminPrefix}Repository`,
                                                          entityServicePath)
        }

        return {
            entityServiceName: `${entityName}${adminPrefix}Repository`,
            namedQueries: namedQueries
        }
    }

    /**
     * Adds invocation logic to named queries and returns the {@link FunctionDefinition}s that define them.
     */
    private async processNamedQueries(entityServiceName: string,
                                      entityServicePath: string): Promise<FunctionDefinition[]>{

        const namedQueries: FunctionDefinition[] = []
        this.tsMorphProject.addSourceFileAtPath(entityServicePath)

        const entityServiceSource = this.tsMorphProject.getSourceFile(entityServicePath)

        // Currently we only support named queries when added to the generated entity service class
        if(entityServiceSource){

            const serviceClass = entityServiceSource.getClass(entityServiceName)
            if(serviceClass){

                // Convert all methods that have a @Query decorator to a C3 FunctionDefinition
                // These will be used to define the named queries for the EntityDefinition
                for(const method of serviceClass?.getInstanceMethods()){

                    const queryDecorator = method.getDecorator('Query')
                    if(queryDecorator){

                        const methodName = method.getName()
                        const functionDefinition = new FunctionDefinition(methodName,
                                                                          [tsDecoratorToC3Decorator(queryDecorator)!])

                        // TODO: add more generic decorator handling
                        const policyDecorator = method.getDecorator('Policy')
                        if(policyDecorator){
                            functionDefinition.addDecorator(tsDecoratorToC3Decorator(policyDecorator)!)
                        }

                        functionDefinition.returnType = this.createC3TypeForReturnType(method.getReturnType())

                        // Find page parameter if any and store all parameter names for later
                        const argNames: string[] = []
                        let pageableParameterName: string | null = null

                        const parameters = method.getParameters()
                        for(let i = 0; i < parameters.length; i++){
                            let parameter = parameters[i]
                            const parameterName = parameter.getName()
                            const parameterTypeName = parameter.getType().getSymbol()?.getName();

                            let parameterC3Type: C3Type
                            if(parameterTypeName === 'Pageable'
                                || parameterTypeName === 'OffsetPageable'
                                || parameterTypeName === 'CursorPageable') {
                                if(i > 0 && i < parameters.length - 1){
                                    this.logger.log(chalk.yellow(`It is best if Pageable is always the first or last parameter.`))
                                }
                                pageableParameterName = parameterName
                                parameterC3Type = new PageableC3Type()
                            } else {
                                argNames.push(parameterName)
                                parameterC3Type = this.conversionContext.convert(parameter.getType())
                            }

                            functionDefinition.addParameter(parameterName, parameterC3Type)
                        }


                        // Code generated is similar to the following
                        // const parameters = [
                        //     {key: 'who', value: who},
                        //     {key: 'howMany', value: howMany}
                        // ]
                        // return this.namedQuery('namedQueryTest', parameters)

                        method.setBodyText(writer => {
                            writer.writeLine('/** Autogenerated code, changes will be overwritten. **/')
                            if(argNames.length > 0) {
                                writer.writeLine('const parameters = [')
                                      .indent(() => {
                                          for (let i = 0; i < argNames.length; i++) {
                                              let argName = argNames[i];
                                              writer.write(`{key: '${argName}', value: ${argName}}`)
                                              if (i != argNames.length - 1) {
                                                  writer.write(',')
                                              }
                                              writer.newLine()
                                          }
                                      })
                                      .writeLine(']')
                            }

                            if(pageableParameterName){
                                writer.writeLine(`return this.namedQueryPage('${methodName}', ${argNames.length > 0 ? 'parameters' : '[]'}, ${pageableParameterName})`)
                            }else{
                                writer.writeLine(`return this.namedQuery('${methodName}', ${argNames.length > 0 ? 'parameters' : '[]'})`)
                            }
                        })

                        namedQueries.push(functionDefinition)
                    }
                }
                await entityServiceSource.save()
            }
        }
        return namedQueries
    }

    private createC3TypeForReturnType(returnType: Type): C3Type {
        let ret: C3Type
        // All methods must return a Promise
        if (returnType.getSymbol()?.getName() === 'Promise') {
            let typeArguments = returnType.getTypeArguments()
            if (typeArguments?.length !== 1) {
                throw new Error('Promise must have exactly one type argument')
            }
            returnType = typeArguments[0]
            // If a Page is being returned then use internal PageC3Type for simplicity
            if(returnType.getSymbol()?.getName() === 'Page'
                || returnType.getSymbol()?.getName() === 'IterablePage'){
                typeArguments = returnType.getTypeArguments()
                if (typeArguments?.length !== 1) {
                    throw new Error('Page must have exactly one type argument')
                }
                const contentType = this.conversionContext.convert(typeArguments[0])
                if(contentType instanceof ObjectC3Type){
                    ret = new PageC3Type(contentType)
                }else{
                    throw new Error('Only Object types are supported for Page content types')
                }
            }else{
                ret = this.conversionContext.convert(returnType)
            }
        } else {
            throw new Error('Only methods that return a Promise are supported for named queries')
        }
        return ret
    }

    private createStatementMapper(entityInfo: EntityInfo): StatementMapper{
        const state = new StatementMapperConversionState(entityInfo.entity.namespace)

        const conversionContext = createConversionContext(new StatementMapperConverterStrategy(state, this.logger))
        return conversionContext.convert(entityInfo.entity)
    }


}
