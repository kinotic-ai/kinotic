import { AnyC3Type, FunctionDefinition, RequirePermissionDecorator } from '@kinotic-ai/idl'
import type { ApplicationServiceContract } from '@kinotic-ai/management-api'
import { Decorator, ClassDeclaration, MethodDeclaration, Project, SyntaxKind } from 'ts-morph'
import path from 'node:path'

/** Compiles only explicit decorator metadata; never infers a resource from the first argument. */
export class ApplicationContractCompiler {
    compile(paths: string[], defaultZone?: string): ApplicationServiceContract[] {
        const project = new Project({ tsConfigFilePath: path.resolve('tsconfig.json'), skipAddingFilesFromTsConfig: true })
        for (const source of paths) project.addSourceFilesAtPaths(source.endsWith('.ts') ? source : path.join(source, '**/*.ts'))
        const contracts: ApplicationServiceContract[] = []
        for (const source of project.getSourceFiles()) for (const service of source.getClasses()) {
            const publish = service.getDecorator('Publish')
            if (!publish) continue
            const namespace = this.literal(publish, 0) ?? null
            const name = this.literal(publish, 1) ?? service.getNameOrThrow()
            const zone = this.literal(service.getDecorator('Zone'), 0) ?? defaultZone ?? null
            const version = this.literal(service.getDecorator('Version'), 0) ?? '1.0.0'
            const functions: FunctionDefinition[] = []
            for (const method of service.getInstanceMethods()) {
                if (method.getScope() === 'private' || method.getScope() === 'protected') continue
                const definition = new FunctionDefinition(method.getName())
                const parameters = method.getParameters()
                const context = method.getDecorator('Context') != null
                for (const parameter of context ? parameters.slice(0, -1) : parameters) definition.addParameter(parameter.getName(), new AnyC3Type())
                const permission = this.permission(service, method)
                if (permission) {
                    permission.argumentIndex = permission.idArgument ? definition.parameters.findIndex(parameter => parameter.name === permission.idArgument.split('.')[0]) : -1
                    if (permission.idArgument && permission.argumentIndex < 0) throw new Error('ResourceTarget cannot refer to an injected context')
                    definition.addDecorator(permission)
                }
                functions.push(definition)
            }
            contracts.push({ namespace, name, zone, version, functions })
        }
        return contracts
    }
    private permission(service: ClassDeclaration, method: MethodDeclaration): RequirePermissionDecorator | null {
        const declaration = method.getDecorator('RequirePermission') ?? service.getDecorator('RequirePermission')
        if (!declaration) return null
        const namespace = this.literal(service.getDecorator('PermissionNamespace'), 0)
        if (!namespace) throw new Error(`PermissionNamespace is required on ${service.getName()}`)
        const type = this.options(method.getDecorator('ResourceTarget')).type ?? this.options(service.getDecorator('ResourceTarget')).type ?? 'scope'
        const target = method.getDecorator('ResourceTarget') ?? service.getDecorator('ResourceTarget')
        const idArgument = this.options(target).idArgument ?? ''
        const options = this.options(declaration, 1)
        const result = new RequirePermissionDecorator()
        result.permission = `${namespace}.${this.literal(declaration, 0) || method.getName()}`
        result.resourceType = String(type)
        result.idArgument = String(idArgument)
        result.label = String(options.label ?? '')
        result.tenantDelegable = options.tenantDelegable === true
        if (idArgument && !method.getParameters().some(parameter => parameter.getName() === String(idArgument).split('.')[0])) throw new Error(`Unknown resource argument on ${method.getName()}`)
        return result
    }
    private literal(decorator: Decorator | undefined, index: number): string | undefined {
        const value = decorator?.getArguments()[index]
        if (!value || value.getKind() === SyntaxKind.NullKeyword || value.getKind() === SyntaxKind.Identifier && value.getText() === 'undefined') return undefined
        if (value.getKind() !== SyntaxKind.StringLiteral && value.getKind() !== SyntaxKind.NoSubstitutionTemplateLiteral) throw new Error('Authorization declarations require literal strings')
        return value.getType().getLiteralValue() as string
    }
    private options(decorator: Decorator | undefined, index = 0): Record<string, string | boolean> {
        const value = decorator?.getArguments()[index]
        if (!value) return {}
        if (value.getKind() !== SyntaxKind.ObjectLiteralExpression) throw new Error('Authorization options require an object literal')
        const result: Record<string, string | boolean> = {}
        for (const property of value.asKindOrThrow(SyntaxKind.ObjectLiteralExpression).getProperties()) {
            if (property.getKind() !== SyntaxKind.PropertyAssignment) throw new Error('Authorization options cannot contain spreads or computed values')
            const assignment = property.asKindOrThrow(SyntaxKind.PropertyAssignment)
            const initializer = assignment.getInitializerOrThrow()
            const literal = initializer.getType().getLiteralValue()
            if (typeof literal === 'string') result[assignment.getName()] = literal
            else if (initializer.getKind() === SyntaxKind.TrueKeyword || initializer.getKind() === SyntaxKind.FalseKeyword) result[assignment.getName()] = initializer.getKind() === SyntaxKind.TrueKeyword
            else throw new Error('Authorization options require literal strings or booleans')
        }
        return result
    }
}
