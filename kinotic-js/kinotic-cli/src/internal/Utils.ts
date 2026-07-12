import {C3Type, FunctionDefinition, ObjectC3Type} from '@kinotic-ai/idl'
import fs from 'fs'
import fsPromises from 'fs/promises'
import path from 'path'
import {IndentationText, Node, Project} from 'ts-morph'
import {createConversionContext} from './converter/IConversionContext'
import {TypescriptConversionState} from './converter/typescript/TypescriptConversionState'
import {TypescriptConverterStrategy} from './converter/typescript/TypescriptConverterStrategy'
import {Logger} from './Logger'

export type GeneratedServiceInfo = {
    entityServiceName: string
    namedQueries: FunctionDefinition[]
}

function isEmpty(value: any): boolean {
    if (value === null || value === undefined) {
        return true;
    }

    if (Array.isArray(value)) {
        return value.every(isEmpty);
    }
    else if (typeof (value) === 'object') {
        return Object.values(value).every(isEmpty);
    }

    return false;
}

export function jsonStringifyReplacer(key: any, value: any) {
    return isEmpty(value)
           ? undefined
           : value;
}

export type EntityInfo = {
    exportedFromFile: string,
    defaultExport: boolean,
    entity: ObjectC3Type
    multiTenantSelectionEnabled: boolean
}

export type ConversionConfiguration = {
    application: string,
    entitiesPath: string,
    verbose: boolean,
    logger: Logger,
}

function getEntityDecoratorIfExists(node: Node){
    if(Node.isClassDeclaration(node)){
        return node.getDecorator('Entity')
    }
}

export function pathToTsGlobPath(path: string): string{
    return path.endsWith('.ts') ? path : (path.endsWith('/') ? path + '*.ts' : path + '/*.ts')
}

export function createTsMorphProject(): Project {
    const tsConfigFilePath = path.resolve('tsconfig.json')
    if(!fs.existsSync(tsConfigFilePath)){
        throw new Error(`No tsconfig.json found in working directory: ${process.cwd()}`)
    }
    return new Project({
       tsConfigFilePath: tsConfigFilePath,
       manipulationSettings: {
           indentationText: IndentationText.TwoSpaces
       }
    })
}

/**
 * Converts all entities found in the given path configuration.
 * @param config the conversion configuration
 * @param changedFiles optional set of absolute file paths to limit processing to. If null, all files are processed.
 */
export function convertAllEntities(config: ConversionConfiguration, changedFiles?: Set<string> | null): EntityInfo[]{
    const entities: EntityInfo[] = []

    const project = createTsMorphProject()

    if(config.verbose) {
        project.enableLogging(true)
    }
    let absEntitiesPath = path.resolve(config.entitiesPath)
    if(!absEntitiesPath.endsWith('.ts') && !absEntitiesPath.endsWith(path.sep)){
        absEntitiesPath = absEntitiesPath + path.sep
    }

    project.addSourceFilesAtPaths(pathToTsGlobPath(config.entitiesPath))

    const sourceFiles = project.getSourceFiles()
    for (const sourceFile of sourceFiles) {

        const absSourcePath = path.resolve(sourceFile.getFilePath())

        // make sure this file is in our configured paths and not just introduced by the ts-config
        if(absSourcePath.startsWith(absEntitiesPath)) {

            // Skip files that haven't changed if incremental mode is active
            if(changedFiles && !changedFiles.has(absSourcePath)) {
                continue
            }

            const conversionContext =
                      createConversionContext(new TypescriptConverterStrategy(new TypescriptConversionState(config.application),
                                                                              config.logger))

            const exportedDeclarations = sourceFile.getExportedDeclarations()
            exportedDeclarations.forEach((exportedDeclarationEntries, name) => {
                exportedDeclarationEntries.forEach((exportedDeclaration) => {
                    if (Node.isClassDeclaration(exportedDeclaration) || Node.isInterfaceDeclaration(exportedDeclaration)) {
                        const declaration = exportedDeclaration

                        // If the Entity is decorated with @Entity or has an EntityConfiguration we convert it
                        const decorator = getEntityDecoratorIfExists(exportedDeclaration)
                        if (decorator) {

                            let c3Type: C3Type | null = null
                            try {
                                conversionContext.state().multiTenantSelectionEnabled = false
                                c3Type = conversionContext.convert(declaration.getType())
                            } catch (e) {
                            } // We ignore this error since the converter will print any errors

                            if (c3Type != null) {

                                if (c3Type instanceof ObjectC3Type) {

                                    entities.push({
                                                      exportedFromFile: declaration.getSourceFile().getFilePath(),
                                                      defaultExport: declaration.isDefaultExport(),
                                                      entity: c3Type,
                                                      multiTenantSelectionEnabled: conversionContext.state().multiTenantSelectionEnabled
                                                  })
                                } else {
                                    throw new Error(`Could not convert ${name} to a C3Type`)
                                }
                            } else {
                                throw new Error(`Could not convert ${name} to a C3Type`)
                            }
                        }
                    }
                })
            })
        }
    }
    return entities
}

/**
 * Will return the relative path from the 'from' path to the 'to' path
 * @param from path to start from
 * @param to path to end at
 * @param fileExtensionForImports this is the file extension to append to the end of the relative path
 */
export function getRelativeImportPath(from: string, to: string, fileExtensionForImports: string = '') {
    if(!from){
        throw new Error('from path is required')
    }
    if(!to){
        throw new Error('to path is required')
    }
    const fromDir = path.dirname(from);
    let relativePath = path.relative(fromDir, to)

    // Normalize path separators to forward slashes for import statements
    // This is required because import statements always use forward slashes,
    // even on Windows, but path.relative() returns platform-specific separators
    relativePath = relativePath.replace(/\\/g, '/')

    // Make sure path starts with './' or '../'
    if (!relativePath.startsWith('../') && !relativePath.startsWith('./')) {
        relativePath = `./${relativePath}`
    }

    // Remove '.ts' extension
    relativePath = relativePath.replace(/\.ts$/, '')
    return relativePath + fileExtensionForImports;
}

/**
 * Will return the name of the node module if the path is within a node module or null if not
 * @param nodeModulePath to check
 */
export function tryGetNodeModuleName(nodeModulePath: string): string | null {
    let ret: string | null = null
    if(nodeModulePath.includes('node_modules')) {
        const nodeModuleIdx = nodeModulePath.indexOf('node_modules/')
        const partBeforeNodeModules = nodeModulePath.slice(0, nodeModuleIdx+13)
        const partAfterNodeModules = nodeModulePath.slice(nodeModuleIdx+13)
        const parts = partAfterNodeModules.split('/')
        let previousPartPath = ''
        for (let part of parts) {
            const packagePath = path.resolve(partBeforeNodeModules, previousPartPath, part, 'package.json')
            if(fs.existsSync(packagePath)){
                const packageJson = JSON.parse(fs.readFileSync(packagePath, 'utf8'))
                ret = packageJson.name
                break
            }else{
                previousPartPath = part + '/'
            }
        }
    }
    return ret
}

/**
 * Saves the C3Type to the local filesystem
 * @param savePath to save the entities to
 * @param entity to save
 * @param logger to log to if desired, if null nothing will be logged
 */
export async function writeEntityJsonToFilesystem(savePath: string, entity: ObjectC3Type, logger?: Logger): Promise<void> {
    const json = JSON.stringify(entity, jsonStringifyReplacer, 2)
    if (json && json.length > 0) {
        const outputPath = path.resolve(savePath, 'generated', 'entity-definitions', `${entity.namespace}.${entity.name}.json`)
        await fsPromises.mkdir(path.dirname(outputPath), {recursive: true})
        await fsPromises.writeFile(outputPath, json)
        if (logger) {
            logger.log(`Wrote ${entity.namespace}.${entity.name} to ${outputPath}`)
        }
    }
}

/**
 * Save the C3Type(s) to the local filesystem
 * @param savePath to save the entities to
 * @param entities to save
 * @param logger to log to if desired, if null nothing will be logged
 */
export async function writeEntitiesJsonToFilesystem(savePath: string, entities: ObjectC3Type[], logger?: Logger): Promise<void> {
    for(const entity of entities){
        await writeEntityJsonToFilesystem(savePath, entity, logger)
    }
}

export async function writeGeneratedServiceInfoToFilesystem(savePath: string, info: GeneratedServiceInfo, logger?: Logger): Promise<void> {
    const json = JSON.stringify(info, jsonStringifyReplacer, 2)
    if (json && json.length > 0) {
        const outputPath = path.resolve(savePath, 'generated', 'query-definitions', `${info.entityServiceName}.json`)
        await fsPromises.mkdir(path.dirname(outputPath), {recursive: true})
        await fsPromises.writeFile(outputPath, json)
        if (logger) {
            logger.log(`Wrote ${info.entityServiceName} named queries to ${outputPath}`)
        }
    }
}
