import {AnyC3Type, AsyncC3Type, C3Type, FunctionDefinition, ServiceDefinition, StreamC3Type, VoidC3Type} from '@kinotic-ai/idl'
import {KinoticProjectConfig} from '@kinotic-ai/management-api'
import fsPromises from 'fs/promises'
import path from 'path'
import {ClassDeclaration, Decorator, MethodDeclaration, Project, Type} from 'ts-morph'
import {createConversionContext, IConversionContext} from './converter/IConversionContext'
import {tsDecoratorToC3Decorator} from './converter/typescript/ConverterUtils'
import {TypescriptConversionState} from './converter/typescript/TypescriptConversionState'
import {TypescriptConverterStrategy} from './converter/typescript/TypescriptConverterStrategy'
import {Logger} from './Logger'
import {createTsMorphProject, jsonStringifyReplacer, pathToTsGlobPath} from './Utils'

/**
 * The module the definitions are written to, under the project's generatedPath.
 */
export const SERVICE_DEFINITIONS_MODULE = 'ServiceDefinitions.ts'

const IDENTIFIER = /^[A-Za-z_$][\w$]*$/

/**
 * Generates the definition of every service the project publishes, the classes decorated with @Publish under
 * the project's servicesPaths: one function per instance method of the class and the classes it extends, with
 * the parameters a request carries, typed as an entity's properties are, and the AuthzResource and AuthzCheck
 * decorators the class declares. The definitions are written to the ServiceDefinitions module, which declares
 * them to the runtime so each service registers in the platform's service directory as it comes online.
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

        for (const decorator of declaration.getDecorators()) {
            const c3Decorator = tsDecoratorToC3Decorator(decorator)
            if (c3Decorator) {
                ret.addDecorator(c3Decorator)
            }
        }

        const state = new TypescriptConversionState(this.application)
        state.shouldAddSourcePathToMetadata = false
        const conversionContext = createConversionContext(new TypescriptConverterStrategy(state, this.logger))
        // the most specific declaration of a method wins, as the instance's prototype chain resolves it
        const converted = new Set<string>()
        let current: ClassDeclaration | undefined = declaration
        while (current) {
            for (const method of current.getInstanceMethods()) {
                if (!method.isOverload() && !converted.has(method.getName())) {
                    converted.add(method.getName())
                    ret.addFunction(this.convertMethod(method, service, conversionContext))
                }
            }
            current = current.getBaseClass()
        }
        return ret
    }

    private convertMethod(method: MethodDeclaration,
                          service: string,
                          conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): FunctionDefinition {
        const ret = new FunctionDefinition(method.getName())
        const where = `${method.getName()} on ${service}`
        for (const decorator of method.getDecorators()) {
            const c3Decorator = tsDecoratorToC3Decorator(decorator)
            if (c3Decorator) {
                ret.addDecorator(c3Decorator)
            }
        }
        const parameters = method.getParameters()
        // the context a @Context method takes last is the platform's, not the caller's
        const carried = method.getDecorator('Context') ? parameters.slice(0, -1) : parameters
        carried.forEach((parameter, index) => {
            // a destructured parameter has no name of its own, so it is named for its position
            const parameterName = IDENTIFIER.test(parameter.getName()) ? parameter.getName() : `arg${index}`
            // an optional parameter's type carries undefined, which a request never sends
            ret.addParameter(parameterName, this.convertType(parameter.getType().getNonNullableType(), conversionContext, `parameter ${parameterName} of ${where}`))
        })
        ret.returnType = this.convertReturnType(method.getReturnType(), conversionContext, where)
        return ret
    }

    // A Promise resolves to its value asynchronously and an Observable streams its values; any other type is
    // returned as it is
    private convertReturnType(returnType: Type,
                              conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>,
                              where: string): C3Type {
        let ret: C3Type
        const symbolName = returnType.getSymbol()?.getName()
        if (symbolName === 'Promise' || symbolName === 'Observable') {
            const typeArguments = returnType.getTypeArguments()
            if (typeArguments.length !== 1) {
                throw new Error(`The return type of ${where} must have exactly one type argument`)
            }
            const valueType = this.convertType(typeArguments[0], conversionContext, `the return type of ${where}`)
            ret = symbolName === 'Promise' ? new AsyncC3Type(valueType) : new StreamC3Type(valueType)
        } else {
            ret = this.convertType(returnType, conversionContext, `the return type of ${where}`)
        }
        return ret
    }

    // A type the schema places no constraint on converts to anything; void to void; the rest as an entity's
    // properties do
    private convertType(type: Type,
                        conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>,
                        where: string): C3Type {
        let ret: C3Type
        if (type.isAny() || type.isUnknown() || type.isNever()) {
            ret = new AnyC3Type()
        } else if (type.isVoid() || type.isUndefined()) {
            ret = new VoidC3Type()
        } else {
            try {
                ret = conversionContext.convert(type)
            } catch (e: any) {
                throw new Error(`Could not convert ${where}: ${e?.message ?? e}`)
            }
        }
        return ret
    }
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
