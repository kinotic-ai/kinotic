import {CalculatedPropertyConfiguration,
        EntityConfiguration,
        TransformConfiguration,
        TypescriptExternalProjectConfig,
        TypescriptProjectConfig
    } from '@kinotic/structures-api'
import type { StructuresProjectConfig } from '@kinotic/structures-api'
import {createStateManager} from './IStateManager.js'
import { loadConfig } from 'c12'
import path from 'path'
import fsPromises from 'fs/promises'
import { Liquid } from 'liquidjs'
import { fileURLToPath } from 'url'

const STRUCTURES_KEY = 'structures'

// FIXME: make sure comments are correct

export class NamespaceConfiguration {
    /**
     * The name of the Namespace that all Structures created for this configuration will be created in.
     */
    namespaceName!: string

    /**
     * The paths to search for classes decorated with @Entity that Structures will be created for.
     */
    entitiesPaths!: string[]

    /**
     * The path to where generated files will be placed.
     */
    generatedPath!: string

    /**
     * External EntityConfigurations that are part of an external codebase and that you cannot add @Entity decorators to.
     */
    externalEntities?: {
        [pathPattern: string] : EntityConfiguration[]
    }

    /**
     * The path to look for files that export functions that can be used by a {@link TransformConfiguration} or {@link CalculatedPropertyConfiguration}.
     */
    utilFunctionsPaths?: string[]

    /**
     * If true the generated EntityService classes will validate all data before sending to the server.
     */
    validate?: boolean
}

export class StructuresProject {

    /**
     * The configurations for all namespaces in this project.
     */
    namespaces: NamespaceConfiguration[] = []

    /**
     * The name of the default namespace.
     * This is the namespace that will be used if no namespace is specified.
     */
    defaultNamespaceName!: string

    /**
     * The file extension to use for imports in generated files.
     */
    fileExtensionForImports: string = '.js'

    hasNamespaceConfig(name: string): boolean {
        return this.findNamespaceConfig(name) !== null
    }

    findNamespaceConfigOrDefault(namespaceName?: string): NamespaceConfiguration {
        let ret: NamespaceConfiguration | null
        if(namespaceName){
            ret = this.findNamespaceConfig(namespaceName)
            if(ret === null){
                throw new Error(`No configured namespace found with name ${namespaceName}`)
            }
        }else{
            ret = this.getDefaultNamespaceConfig()
        }
        return ret
    }

    findNamespaceConfig(namespaceName: string): NamespaceConfiguration | null {
        let ret: NamespaceConfiguration | null = null
        for(const namespace of this.namespaces){
            if(namespace.namespaceName === namespaceName){
                ret = namespace
                break
            }
        }
        return ret
    }

    getDefaultNamespaceConfig(): NamespaceConfiguration {
        if(this.defaultNamespaceName) {
            const ret = this.findNamespaceConfig(this.defaultNamespaceName)
            if (ret === null) {
                throw new Error(`No default namespace found with name ${this.defaultNamespaceName}`)
            }
            return ret
        }else{
            throw new Error('No default namespace name has been set')
        }
    }
}

/**
 * Returns the absolute path to the first supported structures.config.* file in the .config directory, or undefined if none found.
 */
async function findStructuresConfigFile(): Promise<string | undefined> {
    const configDir = path.resolve(process.cwd(), '.config')
    try {
        const stat = await fsPromises.stat(configDir)
        if (stat.isDirectory()) {
            const files = await fsPromises.readdir(configDir)
            const supported = files.filter(f => f.startsWith('structures.config.'))
            if (supported.length > 0) {
                return path.join(configDir, supported[0])
            }
        }
    } catch (e) {
        // Directory does not exist or is not accessible
    }
    return undefined
}

/**
 * Helper function to render a value as TypeScript code with proper indentation
 */
function renderValue(value: any, indent: number = 0): string {
    const indentStr = '  '.repeat(indent)
    const nextIndentStr = '  '.repeat(indent + 1)

    if (value === null) {
        return 'null'
    }
    if (value === undefined) {
        return 'undefined'
    }
    if (typeof value === 'boolean') {
        return String(value)
    }
    if (typeof value === 'string') {
        // JSON.stringify produces a valid JS/TS string literal with correct escaping
        // (handles backslashes, quotes, newlines, tabs, etc.)
        return JSON.stringify(value)
    }
    if (typeof value === 'number') {
        return String(value)
    }
    if (Array.isArray(value)) {
        if (value.length === 0) {
            return '[]'
        }
        const items = value.map(item => `${nextIndentStr}${renderValue(item, indent + 1)}`).join(',\n')
        return `[\n${items}\n${indentStr}]`
    }
    if (typeof value === 'object' && value !== null) {
        const entries = Object.entries(value)
            .filter(([key, val]) => val !== undefined) // Only filter out undefined values

        if (entries.length === 0) {
            return '{}'
        }

        const props = entries
            .map(([key, val]) => `${nextIndentStr}${key}: ${renderValue(val, indent + 1)}`)
            .join(',\n')
        return `{\n${props}\n${indentStr}}`
    }
    return 'undefined'
}

/**
 * Saves a StructuresProjectConfig to the .config directory using the appropriate Liquid template.
 * @param config The config object to save
 * @param configDir The directory to save the config file in (usually .config)
 */
export async function saveStructuresProjectConfig(config: StructuresProjectConfig, configDir: string): Promise<void> {
    const engine = new Liquid({
        root: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../templates/'),
        extname: '.liquid'
    })

    // Register custom tags for rendering config values
    engine.registerTag('render_value', {
        parse: function(tagToken: any) {
            const args = tagToken.args.trim().split(/\s+/)
            this.value = args[0]
            this.indent = args[1] || '0' // Default indent to 0
        },
        render: function*(ctx: any): Generator<any, string, any> {
            const value = yield (engine as any).evalValue(this.value, ctx)
            const indentLevel = yield (engine as any).evalValue(this.indent, ctx)
            return renderValue(value, indentLevel || 0)
        }
    })
    const outFile = path.join(configDir, 'structures.config.ts')
    const templateFile = 'StructuresProjectConfig.ts.liquid'
    const typeName = (config.mdl === 'ts-external') ? 'TypescriptExternalProjectConfig' : 'TypescriptProjectConfig'
    const renderContext = { config, typeName }
    try {
        await fsPromises.mkdir(configDir)
    } catch (e) {
        // Directory may already exist
    }
    const fileContent = await engine.renderFile(templateFile, renderContext)
    await fsPromises.writeFile(outFile, fileContent)
}

// Helper to convert and store legacy config using Liquid templates
async function convertAndStoreLegacyConfig(configDir: string): Promise<StructuresProjectConfig> {
    // Use direct imports for config classes
    const legacy = await loadStructuresProjectLegacy()
    // Always use the first (and only) namespace
    const ns = legacy.namespaces.length > 0 ? legacy.namespaces[0] : undefined
    let newConfig: TypescriptProjectConfig | TypescriptExternalProjectConfig
    const hasExternal = ns && ns.externalEntities && typeof ns.externalEntities === 'object' && !Array.isArray(ns.externalEntities) && Object.keys(ns.externalEntities).length > 0
    if (hasExternal) {
        newConfig = new TypescriptExternalProjectConfig()
    } else {
        newConfig = new TypescriptProjectConfig()
    }
    // Don't set name - it will be loaded from package.json
    newConfig.application = legacy.defaultNamespaceName
    newConfig.entitiesPaths = ns ? ns.entitiesPaths : []
    newConfig.generatedPath = ns ? ns.generatedPath : ''
    newConfig.validate = ns ? ns.validate : undefined
    if (hasExternal && newConfig instanceof TypescriptExternalProjectConfig && ns) {
        newConfig.externalEntities = ns.externalEntities
        if (Array.isArray(ns.utilFunctionsPaths)) {
            newConfig.utilFunctionsPaths = ns.utilFunctionsPaths
        }
    }
    await saveStructuresProjectConfig(newConfig, configDir)
    return newConfig
}

export async function isStructuresProject(): Promise<boolean> {
    let result = false
    if (await findStructuresConfigFile()) {
        result = true
    }
    if (!result) {
        result = await isStructuresProjectLegacy()
    }
    return result
}

export async function loadStructuresProjectConfig(): Promise<StructuresProjectConfig> {
    let result: StructuresProjectConfig | undefined
    const configFile = await findStructuresConfigFile()
    let configDir = path.resolve(process.cwd(), '.config')
    if (configFile) {
        configDir = path.dirname(configFile)
        const { config } = await loadConfig({
            configFile: configFile,
            name: 'structures',
            cwd: configDir,
            dotenv: false,
            packageJson: false
        })
        if (!config) {
            throw new Error(`Failed to load config from ${configFile}`)
        }
        result = config as StructuresProjectConfig
    } else if (await isStructuresProjectLegacy()) {
        result = await convertAndStoreLegacyConfig(configDir)
    }
    if (!result) {
        throw new Error('No structures project config found and not a legacy project')
    }
    // If name is not set, try to load from package.json in cwd
    if (!result.name || !result.description) {
        try {
            const pkgPath = path.resolve(process.cwd(), 'package.json')
            const pkgRaw = await fsPromises.readFile(pkgPath, 'utf-8')
            const pkg = JSON.parse(pkgRaw)
            if (!result.name && pkg.name) {
                result.name = pkg.name
            } else {
                throw new Error('No "name" field found in package.json. Please set the name in your StructuresProjectConfig or package.json.')
            }
            if (!result.description && pkg.description) {
                result.description = pkg.description
            }
        } catch (e) {
            throw new Error('Could not determine project name. Please set the name in your StructuresProjectConfig.\nOriginal error: ' + (e instanceof Error ? e.message : String(e)))
        }
    }
    return result
}

export async function isStructuresProjectLegacy(): Promise<boolean> {
    const stateManager = createStateManager(process.cwd())
    return await stateManager.containsState(STRUCTURES_KEY)
}

export async function loadStructuresProjectLegacy(): Promise<StructuresProject>{
    const stateManager = createStateManager(process.cwd())
    if(await stateManager.containsState(STRUCTURES_KEY)){
        const jsonProject = await stateManager.load<StructuresProject>(STRUCTURES_KEY)
        const ret = new StructuresProject() // we do this to ensure that the object has the correct prototype
        if(jsonProject.namespaces){
            ret.namespaces = jsonProject.namespaces
        }
        ret.defaultNamespaceName = jsonProject.defaultNamespaceName
        return ret
    }else {
        return new StructuresProject()
    }
}

