import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {
    AnyC3Type,
    ArrayC3Type,
    C3Type,
    DateC3Type,
    AsyncC3Type,
    EnumC3Type,
    IntC3Type,
    MapC3Type,
    ObjectC3Type,
    ServiceDefinition,
    StreamC3Type,
    StringC3Type,
    UnionC3Type,
    VoidC3Type
} from '@kinotic-ai/idl'
import {KinoticProjectConfig} from '@kinotic-ai/management-api'
import {ConsoleLogger} from '../../src/internal/Logger.js'
import {SERVICE_DEFINITIONS_MODULE, ServiceDefinitionGenerationService} from '../../src/internal/ServiceDefinitionGenerationService.js'

const SERVICE_SOURCE = `import {Context, Publish, type ServiceContext} from '@kinotic-ai/core'

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
export class ReportService extends BaseReportService {

    override async findReports(): Promise<Report[]> {
        return []
    }

    async generate(reportId: string, options = {draft: false}): Promise<void> {
        void reportId
        void options
    }

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

    inspect(value: unknown, extra?: any): unknown {
        return [value, extra, this.#audit()]
    }

    async #audit(): Promise<void> {
    }
}

export class HelperService {
    help(): string {
        return 'not published'
    }
}
`

const SHAPES_SOURCE = `import {Publish} from '@kinotic-ai/core'

export interface Report {
    id: string
    title: string
}

export interface Summary {
    count: number
}

export interface Page<T> {
    content: T[]
    total: number
}

export enum Status {
    Open = 'OPEN',
    Closed = 'CLOSED'
}

export class ReportModel {
    id = ''
    describe(): string {
        return this.id
    }
}

@Publish('com.acme.shapes')
export class ShapesService {
    async page(page: number): Promise<Page<Report>> {
        return {content: [], total: page}
    }

    async echo<T>(value: T): Promise<T> {
        return value
    }

    async saveAll<T extends Summary>(value: T): Promise<T[]> {
        return [value]
    }

    async partial(): Promise<Partial<Report>> {
        return {}
    }

    async picked(): Promise<Pick<Report, 'id'>> {
        return {id: ''}
    }

    async tagged(): Promise<Report & {tags: string[]}> {
        return {id: '', title: '', tags: []}
    }

    async either(): Promise<Report | Summary> {
        return {count: 0}
    }

    async byStatus(status: Status | null, order: 'asc' | 'desc'): Promise<number> {
        return [status, order].length
    }

    async counts(): Promise<Record<string, number>> {
        return {}
    }

    async since(from: Date): Promise<string> {
        return from.toISOString()
    }

    async saveModel(model: ReportModel): Promise<ReportModel> {
        return model
    }

    then(): PromiseLike<string> {
        return Promise.resolve('')
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

    function generate(warned: string[] = []): Promise<ServiceDefinition[]> {
        const logger = new ConsoleLogger()
        logger.warn = (input: string | Error) => {
            warned.push(input instanceof Error ? input.message : input)
            return input
        }
        return new ServiceDefinitionGenerationService(projectConfig.applicationId, logger).generateAll(projectConfig, false)
    }

    async function generateError(): Promise<string> {
        let ret = ''
        try {
            await generate()
        } catch (e) {
            ret = (e as Error).message
        }
        return ret
    }

    function writeService(source: string, file = 'ShapesService.ts'): void {
        fs.writeFileSync(path.join(projectDir, 'src/services', file), source)
    }

    function valueOf(definition: ServiceDefinition, name: string): C3Type {
        return (fn(definition, name).returnType as AsyncC3Type).valueType
    }

    function fn(definition: ServiceDefinition, name: string) {
        const ret = definition.functions.find(f => f.name === name)
        expect(ret, name).to.not.be.undefined
        return ret!
    }

    it('generates the definition of every published service with its functions', async () => {
        const definitions = await generate()

        expect(definitions.map(d => d.name)).to.deep.equal(['ReportService', 'Probe'])
        const reports = definitions[0]
        expect(reports.namespace).to.equal('com.acme.reports')
        // the class's own methods first, then the ones it inherits and does not override
        expect(reports.functions.map(f => f.name)).to.deep.equal(['findReports', 'generate', 'ping', 'describe', 'archive', 'watch', 'countReports'])

        const generateFn = fn(reports, 'generate')
        expect(generateFn.parameters.map(p => p.name)).to.deep.equal(['reportId', 'options'])
        expect(generateFn.parameters[0].type).to.be.instanceOf(StringC3Type)
        expect(generateFn.parameters[1].type).to.be.instanceOf(ObjectC3Type)
        expect((generateFn.parameters[1].type as ObjectC3Type).properties.map(p => p.name)).to.deep.equal(['draft'])
        expect(generateFn.returnType).to.be.instanceOf(AsyncC3Type)
        expect((generateFn.returnType as AsyncC3Type).valueType).to.be.instanceOf(VoidC3Type)

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
        // an ES private method is not served, so it is not a function of the service
        expect(probe.functions.map(f => f.name)).to.deep.equal(['echo', 'hang', 'inspect'])
        expect(fn(probe, 'inspect').parameters.map(p => p.type)).to.satisfy((types: unknown[]) => types.every(t => t instanceof AnyC3Type))
        expect(fn(probe, 'inspect').returnType).to.be.instanceOf(AnyC3Type)
        expect(fn(probe, 'echo').returnType).to.be.instanceOf(StringC3Type)
        expect((fn(probe, 'hang').returnType as AsyncC3Type).valueType).to.be.instanceOf(AnyC3Type)
    })

    it('describes the tool of every function an @McpTool exposes, on the method or on the class', async () => {
        writeService(`import {McpTool, Publish} from '@kinotic-ai/core'

@Publish('com.acme.orders')
export class OrderService {

    /**
     * Finds an order by its id.
     */
    @McpTool()
    async findById(orderId: string): Promise<string> {
        return orderId
    }

    @McpTool({title: 'Push to ERP', description: 'Sends the order to the ERP', openWorldHint: true})
    async saveToErp(orderId: string): Promise<void> {
        void orderId
    }

    async cancel(orderId: string): Promise<void> {
        void orderId
    }
}

@McpTool({title: 'Stock'})
@Publish('com.acme.orders')
export class StockService {

    /** How many of the item are on hand. */
    async itemCount(sku: string): Promise<number> {
        return sku.length
    }

    async deleteItem(sku: string): Promise<void> {
        void sku
    }

    async createItemIfNotExist(sku: string): Promise<void> {
        void sku
    }

    async getOrCreateBin(sku: string): Promise<string> {
        return sku
    }
}
`, 'OrderService.ts')

        const definitions = await generate()
        const orders = definitions.find(d => d.name === 'OrderService')!
        const stock = definitions.find(d => d.name === 'StockService')!
        const hints = (readOnlyHint: boolean, destructiveHint: boolean, idempotentHint: boolean, openWorldHint = false) =>
            ({readOnlyHint, destructiveHint, idempotentHint, openWorldHint})

        // a method's own declaration describes it outright: what it leaves empty comes from the method, and its hints are as written
        expect(fn(orders, 'findById').decorators).to.deep.equal(
            [{type: 'McpTool', title: 'Order Service Find By Id', description: 'Finds an order by its id.', ...hints(false, false, false)}])
        expect(fn(orders, 'saveToErp').decorators).to.deep.equal(
            [{type: 'McpTool', title: 'Order Service Push to ERP', description: 'Sends the order to the ERP', ...hints(false, false, false, true)}])
        expect(fn(orders, 'cancel').decorators ?? []).to.be.empty

        // the class exposes every function, each hinted by its name
        expect(fn(stock, 'itemCount').decorators).to.deep.equal(
            [{type: 'McpTool', title: 'Stock Item Count', description: 'How many of the item are on hand.', ...hints(true, false, false)}])
        expect(fn(stock, 'deleteItem').decorators).to.deep.equal(
            [{type: 'McpTool', title: 'Stock Delete Item', description: 'Delete item', ...hints(false, true, true)}])
        expect(fn(stock, 'createItemIfNotExist').decorators![0]).to.include(hints(false, false, true))
        expect(fn(stock, 'getOrCreateBin').decorators![0]).to.include(hints(false, false, false))
    })

    it('refuses a tool that streams its result', async () => {
        writeService(`import {McpTool, Publish} from '@kinotic-ai/core'

export interface Observable<T> {
    subscribe(next: (value: T) => void): void
}

@Publish('com.acme.orders')
export class FeedService {
    @McpTool()
    watch(): Observable<string> {
        return {subscribe: () => undefined}
    }
}
`, 'FeedService.ts')

        expect(await generateError()).to.contain('watch on com.acme.orders.FeedService streams its result')
    })

    it('writes the module that declares the definitions to the runtime', async () => {
        await generate()

        const module = fs.readFileSync(path.join(projectDir, 'src/generated', SERVICE_DEFINITIONS_MODULE), 'utf8')
        expect(module).to.contain("import {declareServiceDefinitions} from '@kinotic-ai/core'")
        expect(module).to.contain('declareServiceDefinitions(JSON.parse(`')
        expect(module).to.contain('"name": "ReportService"')
        expect(module).to.not.contain('HelperService')
    })

    it('generates nothing for a project configuring no services paths', async () => {
        projectConfig.servicesPaths = undefined

        expect(await generate()).to.deep.equal([])
        expect(fs.existsSync(path.join(projectDir, 'src/generated'))).to.equal(false)
    })

    it('converts the generic, mapped, intersected and union types a service carries', async () => {
        writeService(SHAPES_SOURCE)
        const warned: string[] = []

        const shapes = (await generate(warned)).find(d => d.name === 'ShapesService')!

        const page = valueOf(shapes, 'page') as ObjectC3Type
        expect(page).to.be.instanceOf(ObjectC3Type)
        expect(page.name).to.equal('Page_Report')
        const content = page.properties.find(p => p.name === 'content')!.type as ArrayC3Type
        expect((content.contains as ObjectC3Type).name).to.equal('Report')
        // a type parameter is what its constraint allows, anything when it has none
        expect(fn(shapes, 'echo').parameters[0].type).to.be.instanceOf(AnyC3Type)
        expect(valueOf(shapes, 'echo')).to.be.instanceOf(AnyC3Type)
        expect((fn(shapes, 'saveAll').parameters[0].type as ObjectC3Type).name).to.equal('Summary')
        expect(((valueOf(shapes, 'saveAll') as ArrayC3Type).contains as ObjectC3Type).name).to.equal('Summary')

        expect((valueOf(shapes, 'partial') as ObjectC3Type).name).to.equal('Partial_Report')
        const picked = valueOf(shapes, 'picked') as ObjectC3Type
        expect(picked.properties.map(p => p.name)).to.deep.equal(['id'])
        const tagged = valueOf(shapes, 'tagged') as ObjectC3Type
        expect(tagged.properties.map(p => p.name)).to.deep.equal(['id', 'title', 'tags'])
        const either = valueOf(shapes, 'either') as UnionC3Type
        expect(either).to.be.instanceOf(UnionC3Type)
        expect(either.types.map(t => t.name)).to.deep.equal(['Report', 'Summary'])

        const byStatus = fn(shapes, 'byStatus')
        expect(byStatus.parameters[0].type).to.be.instanceOf(EnumC3Type)
        // a union of string literals is a string, as it is on an entity
        expect(byStatus.parameters[1].type).to.be.instanceOf(StringC3Type)
        const counts = valueOf(shapes, 'counts') as MapC3Type
        expect(counts).to.be.instanceOf(MapC3Type)
        expect(counts.key).to.be.instanceOf(StringC3Type)
        expect(counts.value).to.be.instanceOf(IntC3Type)

        // a class instance travels as its fields
        expect((valueOf(shapes, 'saveModel') as ObjectC3Type).properties.map(p => p.name)).to.deep.equal(['id'])
        expect(fn(shapes, 'then').returnType).to.be.instanceOf(AsyncC3Type)
        expect(valueOf(shapes, 'then')).to.be.instanceOf(StringC3Type)
    })

    it('publishes a Date as a date with a warning in the log and the directory that it arrives as a string', async () => {
        writeService(SHAPES_SOURCE)
        const warned: string[] = []

        const shapes = (await generate(warned)).find(d => d.name === 'ShapesService')!

        const since = fn(shapes, 'since')
        expect(since.parameters[0].type).to.be.instanceOf(DateC3Type)
        expect(warned).to.have.length(1)
        expect(warned[0]).to.contain('A Date in ShapesService.sinceFrom arrives as a string')
                         .and.to.contain('ISO-8601')
        expect(since.parameters[0].metadata).to.deep.equal({warnings: warned})
        expect(since.metadata).to.be.null
        expect(fn(shapes, 'page').parameters[0].metadata).to.be.null
    })

    for (const [declared, reason] of [
        ['Map<string, number>', 'declare a Record or an array'],
        ['Set<string>', 'declare an array'],
        ['Uint8Array', 'declare a base64 string'],
        ['bigint', 'declare a number or a string'],
        ['[string, number]', 'declare an array or an object'],
        ['(value: string) => void', 'a caller cannot send or receive one']
    ]) {
        it(`refuses a parameter typed ${declared}`, async () => {
            writeService(`import {Publish} from '@kinotic-ai/core'

@Publish('com.acme.shapes')
export class RefusedService {
    async take(value: ${declared}): Promise<void> {
        void value
    }
}
`)

            const message = await generateError()

            expect(message).to.contain('Could not convert parameter value of take on com.acme.shapes.RefusedService')
                           .and.to.contain(reason)
        })
    }

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
