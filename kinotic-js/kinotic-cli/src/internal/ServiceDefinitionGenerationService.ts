import {
    AnyC3Type,
    AsyncC3Type,
    C3Type,
    FunctionDefinition,
    McpToolDecorator,
    type MetadataType,
    ParameterDefinition,
    ServiceDefinition,
    StreamC3Type,
    VoidC3Type
} from '@kinotic-ai/idl'
import {KinoticProjectConfig} from '@kinotic-ai/management-api'
import fsPromises from 'fs/promises'
import path from 'path'
import {ClassDeclaration, Decorator, MethodDeclaration, ObjectLiteralExpression, Project, SyntaxKind, Type} from 'ts-morph'
import {createConversionContext, IConversionContext} from './converter/IConversionContext'
import {TypescriptConversionState} from './converter/typescript/TypescriptConversionState'
import {convertToObjectLiteral} from './converter/typescript/ConverterUtils'
import {ServiceValueToC3Type} from './converter/typescript/ServiceValueToC3Type'
import {TypescriptConverterStrategy} from './converter/typescript/TypescriptConverterStrategy'
import {Logger} from './Logger'
import {createTsMorphProject, jsonStringifyReplacer, pathToTsGlobPath} from './Utils'

/**
 * The module the definitions are written to, under the project's generatedPath.
 */
export const SERVICE_DEFINITIONS_MODULE = 'ServiceDefinitions.ts'

const IDENTIFIER = /^[A-Za-z_$][\w$]*$/

// The metadata key of the warnings a parameter's or a function's return type raised, for the directory's readers to show
const WARNINGS_METADATA_KEY = 'warnings'

// The words of a function name that hint a tool's behavior, the rules the platform reads its own services'
// names by (McpToolHints in kinotic-idl)
const READ_ONLY_VERBS = new Set(['count', 'exists', 'fetch', 'find', 'get', 'has', 'is', 'list', 'load', 'query', 'read', 'search'])
// a verb that replaces or removes state: repeating it lands on the same state
const DESTRUCTIVE_VERBS = new Set(['clear', 'delete', 'destroy', 'drop', 'purge', 'put', 'remove', 'save', 'set', 'truncate',
                                   'update', 'upsert'])
// a verb that changes state additively: no hint of its own, but it disqualifies a reading verb in the same name
const ADDITIVE_VERBS = new Set(['add', 'apply', 'create', 'execute', 'generate', 'import', 'insert', 'invoke', 'publish',
                                'register', 'retry', 'run', 'send', 'start', 'stop', 'submit', 'trigger', 'upload'])

/**
 * Generates the definition of every service the project publishes, the classes decorated with @Publish under
 * the project's servicesPaths: one function per instance method of the class and the classes it extends, with
 * the parameters a request carries, typed as an entity's properties are, and the tool a function is exposed as
 * by an @McpTool on its method or on the class. The definitions are written to the
 * ServiceDefinitions module, which declares them to the runtime so each service registers in the platform's
 * service directory as it comes online.
 */
export class ServiceDefinitionGenerationService {

    private readonly application: string
    private readonly logger: Logger

    constructor(application: string, logger: Logger) {
        this.application = application
        this.logger = logger
    }

    /**
     * Generates the definitions of the services under the project's servicesPaths and writes the module to the
     * project's generatedPath; a project configuring no servicesPaths generates nothing.
     * @param projectConfig the project
     * @param verbose whether to log each service generated
     * @return the definitions generated, in the order the services were found
     */
    public async generateAll(projectConfig: KinoticProjectConfig, verbose: boolean): Promise<ServiceDefinition[]> {
        const ret: ServiceDefinition[] = []
        if (projectConfig.servicesPaths && projectConfig.servicesPaths.length > 0) {
            if (!projectConfig.generatedPath) {
                throw new Error('servicesPaths is configured but no generatedPath is, which the ServiceDefinitions module is written to')
            }
            const project = createTsMorphProject()
            for (const servicesPath of projectConfig.servicesPaths) {
                for (const declaration of this.findServices(project, servicesPath)) {
                    const definition = this.convertService(declaration.declaration, declaration.publish)
                    this.logger.logVerbose(`Generated the definition of ${definition.namespace}.${definition.name}`, verbose)
                    ret.push(definition)
                }
            }
            const modulePath = path.resolve(projectConfig.generatedPath, SERVICE_DEFINITIONS_MODULE)
            await fsPromises.mkdir(path.dirname(modulePath), {recursive: true})
            await fsPromises.writeFile(modulePath, renderModule(ret))
            this.logger.logVerbose(`Wrote ${ret.length} service definitions to ${modulePath}`, verbose)
        }
        return ret
    }

    // Every class decorated with @Publish in the files under the path, in source order
    private findServices(project: Project, servicesPath: string): {declaration: ClassDeclaration, publish: Decorator}[] {
        const ret: {declaration: ClassDeclaration, publish: Decorator}[] = []
        let absServicesPath = path.resolve(servicesPath)
        if (!absServicesPath.endsWith('.ts') && !absServicesPath.endsWith(path.sep)) {
            absServicesPath = absServicesPath + path.sep
        }
        for (const sourceFile of project.addSourceFilesAtPaths(pathToTsGlobPath(servicesPath))) {
            // the files the program pulled in through imports are not the project's services
            if (path.resolve(sourceFile.getFilePath()).startsWith(absServicesPath)) {
                for (const declaration of sourceFile.getClasses()) {
                    const publish = declaration.getDecorator('Publish')
                    if (publish) {
                        ret.push({declaration, publish})
                    }
                }
            }
        }
        return ret
    }

    private convertService(declaration: ClassDeclaration, publish: Decorator): ServiceDefinition {
        const className = declaration.getName()
        if (!className) {
            throw new Error(`A class decorated with @Publish in ${declaration.getSourceFile().getFilePath()} has no name`)
        }
        // @Publish(namespace?, name?, advertise?): the name defaults to the class name, as the runtime defaults it
        const args = publish.getArguments()
        const namespace = args.length > 0 ? literalString(args[0], `the namespace of @Publish on ${className}`) : null
        const name = (args.length > 1 ? literalString(args[1], `the name of @Publish on ${className}`) : null) || className
        const ret = new ServiceDefinition(name, namespace as string)
        const service = `${namespace ? namespace + '.' : ''}${name}`

        const state = new TypescriptConversionState(this.application)
        state.shouldAddSourcePathToMetadata = false
        // a union with no name of its own is named for the service and the function or parameter carrying it
        state.objectNameStack.push(name)
        const serviceTool = declaration.getDecorator('McpTool')
        // the service's half leads every tool's title
        const serviceTitle = (serviceTool ? mcpToolOptions(serviceTool, service).title : null) || titleCase(name)
        const conversion = createConversionContext(new TypescriptConverterStrategy(state, this.logger, [new ServiceValueToC3Type(this.logger)]))
        // the most specific declaration of a method wins, as the instance's prototype chain resolves it
        const seen = new Set<string>()
        let current: ClassDeclaration | undefined = declaration
        while (current) {
            for (const method of current.getInstanceMethods()) {
                // the runtime serves the methods on the prototype chain, which holds no ES private (#) method
                if (!method.isOverload() && !method.getName().startsWith('#') && !seen.has(method.getName())) {
                    seen.add(method.getName())
                    const converted = this.convertMethod(method, service, conversion)
                    const methodTool = method.getDecorator('McpTool')
                    if (methodTool || serviceTool) {
                        const where = `${method.getName()} on ${service}`
                        if (converted.returnType instanceof StreamC3Type) {
                            throw new Error(`${where} streams its result, which an MCP tool cannot`)
                        }
                        converted.addDecorator(mcpToolOf(method, methodTool ? mcpToolOptions(methodTool, where) : null, serviceTitle))
                    }
                    ret.addFunction(converted)
                }
            }
            current = current.getBaseClass()
        }
        return ret
    }

    private convertMethod(method: MethodDeclaration,
                          service: string,
                          conversion: IConversionContext<Type, C3Type, TypescriptConversionState>): FunctionDefinition {
        const ret = new FunctionDefinition(method.getName())
        const where = `${method.getName()} on ${service}`
        const parameters = method.getParameters()
        // the context a @Context method takes last is the platform's, not the caller's
        const carried = method.getDecorator('Context') ? parameters.slice(0, -1) : parameters
        carried.forEach((parameter, index) => {
            // a destructured parameter has no name of its own, so it is named for its position
            const parameterName = IDENTIFIER.test(parameter.getName()) ? parameter.getName() : `arg${index}`
            const type = this.convertType(parameter.getType(),
                                          conversion,
                                          `${method.getName()}${capitalize(parameterName)}`,
                                          `parameter ${parameterName} of ${where}`)
            ret.parameters.push(new ParameterDefinition(parameterName, type, undefined, takeWarnings(conversion.state())))
        })
        ret.returnType = this.convertReturnType(method.getReturnType(), conversion, method.getName(), where)
        ret.metadata = takeWarnings(conversion.state())
        return ret
    }

    // A Promise or PromiseLike resolves to its value asynchronously and an Observable streams its values; any
    // other type is returned as it is
    private convertReturnType(returnType: Type,
                              conversion: IConversionContext<Type, C3Type, TypescriptConversionState>,
                              functionName: string,
                              where: string): C3Type {
        let ret: C3Type
        const symbolName = returnType.getSymbol()?.getName()
        const label = `${functionName}Result`
        if (symbolName === 'Promise' || symbolName === 'PromiseLike' || symbolName === 'Observable') {
            const typeArguments = returnType.getTypeArguments()
            if (typeArguments.length !== 1) {
                throw new Error(`The return type of ${where} must have exactly one type argument`)
            }
            const valueType = this.convertType(typeArguments[0], conversion, label, `the return type of ${where}`)
            ret = symbolName === 'Observable' ? new StreamC3Type(valueType) : new AsyncC3Type(valueType)
        } else {
            ret = this.convertType(returnType, conversion, label, `the return type of ${where}`)
        }
        return ret
    }

    // A type the schema places no constraint on converts to anything; void to void; the rest as an entity's
    // properties do, without the null or undefined an optional parameter or a nullable value carries
    private convertType(type: Type,
                        conversion: IConversionContext<Type, C3Type, TypescriptConversionState>,
                        label: string,
                        where: string): C3Type {
        let ret: C3Type
        if (type.isAny() || type.isUnknown() || type.isNever()) {
            ret = new AnyC3Type()
        } else if (type.isVoid() || type.isUndefined()) {
            ret = new VoidC3Type()
        } else {
            const state = conversion.state()
            state.unionPropertyNameStack.push(label)
            try {
                // NonNullable<T> of a type parameter is T & {}, which hides the parameter's constraint
                ret = conversion.convert(type.isTypeParameter() ? type : type.getNonNullableType())
            } catch (e: any) {
                throw new Error(`Could not convert ${where}: ${e?.message ?? e}`)
            } finally {
                state.unionPropertyNameStack.pop()
            }
        }
        return ret
    }
}

// The warnings a conversion raised, as the metadata of the parameter or function whose type raised them
function takeWarnings(state: TypescriptConversionState): MetadataType | null {
    const warnings = state.warnings.splice(0)
    return warnings.length > 0 ? {[WARNINGS_METADATA_KEY]: warnings} : null
}

// What an @McpTool states: its one argument, an object literal of literals, or nothing
function mcpToolOptions(decorator: Decorator, where: string): Partial<McpToolDecorator> {
    let ret: Partial<McpToolDecorator> = {}
    const argument = decorator.getArguments()[0]
    if (argument) {
        if (argument.getKind() !== SyntaxKind.ObjectLiteralExpression) {
            throw new Error(`@McpTool on ${where} must take an object literal but took ${argument.getText()}`)
        }
        ret = convertToObjectLiteral(argument as ObjectLiteralExpression)
    }
    return ret
}

// The tool a function is exposed as. Its own @McpTool describes it outright, hints included; a function
// exposed by the class alone is hinted by its name. A title or description left empty comes from the function:
// its name, and its JSDoc before its name as a sentence
function mcpToolOf(method: MethodDeclaration, options: Partial<McpToolDecorator> | null, serviceTitle: string): McpToolDecorator {
    const name = method.getName()
    const description = options?.description || method.getJsDocs().map(doc => doc.getDescription().trim()).find(doc => doc)
                        || sentenceCase(name)
    const ret = new McpToolDecorator(`${serviceTitle} ${options?.title || titleCase(name)}`, description)
    if (options) {
        ret.readOnlyHint = options.readOnlyHint === true
        ret.destructiveHint = options.destructiveHint === true
        ret.idempotentHint = options.idempotentHint === true
        ret.openWorldHint = options.openWorldHint === true
    } else {
        // a mutating word anywhere outranks a reading one, so getOrCreatePerson is never served as read-only
        const words = camelCaseWords(name).map(word => word.toLowerCase())
        if (words.some(word => DESTRUCTIVE_VERBS.has(word))) {
            ret.destructiveHint = true
            ret.idempotentHint = true
        } else if (name.endsWith('IfNotExist') || name.endsWith('IfNotExists')) {
            ret.idempotentHint = true
        } else if (!words.some(word => ADDITIVE_VERBS.has(word))) {
            ret.readOnlyHint = words.some(word => READ_ONLY_VERBS.has(word))
        }
    }
    return ret
}

// The words of a camel case identifier, an acronym staying one word: CRIResolver is CRI and Resolver
function camelCaseWords(name: string): string[] {
    return name.match(/[A-Z]+(?![a-z])|[A-Z]?[a-z]+|[0-9]+/g) ?? [name]
}

// findByRepoFullName as "Find By Repo Full Name"
function titleCase(name: string): string {
    return camelCaseWords(name).map(capitalize).join(' ')
}

// findByCRI as "Find by CRI": only the first word capitalized, an acronym keeping its case
function sentenceCase(name: string): string {
    return camelCaseWords(name)
        .map((word, index) => index === 0 ? capitalize(word) : (word === word.toUpperCase() ? word : word.charAt(0).toLowerCase() + word.slice(1)))
        .join(' ')
}

function capitalize(value: string): string {
    return value.charAt(0).toUpperCase() + value.slice(1)
}

// A string literal argument's value, null for a null or undefined argument
function literalString(argument: {getText(): string, getType(): Type}, what: string): string | null {
    let ret: string | null
    const text = argument.getText()
    if (text === 'null' || text === 'undefined') {
        ret = null
    } else {
        const value = argument.getType().getLiteralValue()
        if (typeof value !== 'string') {
            throw new Error(`${what} must be a string literal but was ${text}`)
        }
        ret = value
    }
    return ret
}

// The module's source: the definitions as JSON, declared to the runtime as the module loads
function renderModule(definitions: ServiceDefinition[]): string {
    const json = JSON.stringify(definitions, jsonStringifyReplacer, 2) || '[]'
    const escaped = json.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${')
    return `/** Autogenerated by kinotic sync, changes will be overwritten. **/
import {declareServiceDefinitions} from '@kinotic-ai/core'

declareServiceDefinitions(JSON.parse(\`${escaped}\`))
`
}
