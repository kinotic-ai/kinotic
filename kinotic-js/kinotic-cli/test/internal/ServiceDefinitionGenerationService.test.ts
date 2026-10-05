import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {
    AnyC3Type,
    ArrayC3Type,
    AsyncC3Type,
    AuthzCheckDecorator,
    AuthzResourceDecorator,
    ObjectC3Type,
    ServiceDefinition,
    StreamC3Type,
    StringC3Type,
    VoidC3Type
} from '@kinotic-ai/idl'
import {KinoticProjectConfig} from '@kinotic-ai/management-api'
import {ConsoleLogger} from '../../src/internal/Logger.js'
import {SERVICE_DEFINITIONS_MODULE, ServiceDefinitionGenerationService} from '../../src/internal/ServiceDefinitionGenerationService.js'

const SERVICE_SOURCE = `import {AuthzCheck, AuthzResource, Context, Publish, type ServiceContext} from '@kinotic-ai/core'

export interface Report {
    id: string
    title: string
}

export interface Observable<T> {
    subscribe(next: (value: T) => void): void
}

class BaseReportService {
    async findReports(): Promise<Report[]> {
        return []
    }

    async countReports(): Promise<number> {
        return 0
    }
}

@Publish('com.acme.reports')
@AuthzResource({value: 'report', parent: 'tenant', roles: [{id: 'report.generator', permissions: ['can_generate']}]})
export class ReportService extends BaseReportService {

    override async findReports(): Promise<Report[]> {
        return []
    }

    @AuthzCheck({permission: 'can_generate', consistent: true})
    async generate(reportId: string, options = {draft: false}): Promise<void> {
        void reportId
        void options
    }

    @AuthzCheck({zoneOnly: true})
    ping(): Promise<string> {
        return Promise.resolve('pong')
    }

    @Context
    async describe(reportId: string, context: ServiceContext): Promise<string> {
        return reportId + String(context)
    }

    archive({reason}: {reason: string}, ...tags: string[]): Promise<void> {
        return Promise.resolve(void [reason, tags])
    }

    watch(): Observable<Report> {
        return {subscribe: () => undefined}
    }
}

@Publish(null, 'Probe', true)
export class ProbeService {
    echo(value: string): string {
        return value
    }

    hang(): Promise<never> {
        return new Promise(() => {})
    }
}

export class HelperService {
    help(): string {
        return 'not published'
    }
}
`

describe('ServiceDefinitionGenerationService', () => {

    const projectConfig = new KinoticProjectConfig()
    let projectDir: string
    let originalCwd: string

    // The service resolves the services paths and the tsconfig against the working directory, so each test
    // runs from inside a throwaway project rather than the repo.
    beforeEach(() => {
        originalCwd = process.cwd()
        projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kinotic-services-'))
        fs.mkdirSync(path.join(projectDir, 'src/services'), {recursive: true})
        fs.writeFileSync(path.join(projectDir, 'src/services/ReportService.ts'), SERVICE_SOURCE)
        // The decorators are recognized through the installed @kinotic-ai/core
        fs.symlinkSync(fileURLToPath(new URL('../../node_modules', import.meta.url)), path.join(projectDir, 'node_modules'))
        fs.writeFileSync(path.join(projectDir, 'tsconfig.json'), JSON.stringify({
            compilerOptions: {
                target: 'esnext',
                module: 'ES2020',
                moduleResolution: 'bundler',
                skipLibCheck: true,
                strict: true
            },
            files: []
        }))
        projectConfig.applicationId = 'my.app'
        projectConfig.organizationId = 'acme'
        projectConfig.entitiesPaths = []
        projectConfig.servicesPaths = ['src/services']
        projectConfig.generatedPath = 'src/generated'
        process.chdir(projectDir)
    })

    afterEach(() => {
        process.chdir(originalCwd)
        fs.rmSync(projectDir, {recursive: true, force: true})
    })

    function generate(): Promise<ServiceDefinition[]> {
        return new ServiceDefinitionGenerationService(projectConfig.applicationId, new ConsoleLogger()).generateAll(projectConfig, false)
    }

    function fn(definition: ServiceDefinition, name: string) {
        const ret = definition.functions.find(f => f.name === name)
        expect(ret, name).to.not.be.undefined
        return ret!
    }

    it('generates the definition of every published service with its functions and declarations', async () => {
        const definitions = await generate()

        expect(definitions.map(d => d.name)).to.deep.equal(['ReportService', 'Probe'])
        const reports = definitions[0]
        expect(reports.namespace).to.equal('com.acme.reports')
        const resource = reports.decorators![0] as AuthzResourceDecorator
        expect(resource.type).to.equal('AuthzResource')
        expect(resource.resourceType).to.equal('report')
        expect(resource.parent).to.equal('tenant')
        expect(resource.roles).to.deep.equal([{id: 'report.generator', permissions: ['can_generate']}])
        // the class's own methods first, then the ones it inherits and does not override
        expect(reports.functions.map(f => f.name)).to.deep.equal(['findReports', 'generate', 'ping', 'describe', 'archive', 'watch', 'countReports'])

        const generateFn = fn(reports, 'generate')
        expect(generateFn.parameters.map(p => p.name)).to.deep.equal(['reportId', 'options'])
        expect(generateFn.parameters[0].type).to.be.instanceOf(StringC3Type)
        expect(generateFn.parameters[1].type).to.be.instanceOf(ObjectC3Type)
        expect((generateFn.parameters[1].type as ObjectC3Type).properties.map(p => p.name)).to.deep.equal(['draft'])
        expect(generateFn.returnType).to.be.instanceOf(AsyncC3Type)
        expect((generateFn.returnType as AsyncC3Type).valueType).to.be.instanceOf(VoidC3Type)
        const check = generateFn.decorators![0] as AuthzCheckDecorator
        expect(check.type).to.equal('AuthzCheck')
        expect(check.permission).to.equal('can_generate')
        expect(check.consistent).to.equal(true)
        expect(check.zoneOnly).to.equal(false)

        expect((fn(reports, 'ping').decorators![0] as AuthzCheckDecorator).zoneOnly).to.equal(true)
        // the context a @Context method takes last is the platform's, not the caller's
        expect(fn(reports, 'describe').parameters.map(p => p.name)).to.deep.equal(['reportId'])
        // a destructured parameter has no name of its own, a rest parameter keeps its name
        const archive = fn(reports, 'archive')
        expect(archive.parameters.map(p => p.name)).to.deep.equal(['arg0', 'tags'])
        expect(archive.parameters[1].type).to.be.instanceOf(ArrayC3Type)
        const watch = fn(reports, 'watch')
        expect(watch.returnType).to.be.instanceOf(StreamC3Type)
        expect((watch.returnType as StreamC3Type).valueType).to.be.instanceOf(ObjectC3Type)
        expect((fn(reports, 'findReports').returnType as AsyncC3Type).valueType).to.be.instanceOf(ArrayC3Type)

        const probe = definitions[1]
        expect(probe.namespace).to.be.null
        expect(fn(probe, 'echo').returnType).to.be.instanceOf(StringC3Type)
        expect((fn(probe, 'hang').returnType as AsyncC3Type).valueType).to.be.instanceOf(AnyC3Type)
    })

    it('writes the module that declares the definitions to the runtime', async () => {
        await generate()

        const module = fs.readFileSync(path.join(projectDir, 'src/generated', SERVICE_DEFINITIONS_MODULE), 'utf8')
        expect(module).to.contain("import {declareServiceDefinitions} from '@kinotic-ai/core'")
        expect(module).to.contain('declareServiceDefinitions(JSON.parse(`')
        expect(module).to.contain('"name": "ReportService"')
        expect(module).to.contain('"type": "AuthzResource"')
        expect(module).to.not.contain('HelperService')
    })

    it('generates nothing for a project configuring no services paths', async () => {
        projectConfig.servicesPaths = undefined

        expect(await generate()).to.deep.equal([])
        expect(fs.existsSync(path.join(projectDir, 'src/generated'))).to.equal(false)
    })

    it('refuses services paths without a generated path', async () => {
        projectConfig.generatedPath = undefined

        let message = ''
        try {
            await generate()
        } catch (e) {
            message = (e as Error).message
        }
        expect(message).to.contain('generatedPath')
    })
})
