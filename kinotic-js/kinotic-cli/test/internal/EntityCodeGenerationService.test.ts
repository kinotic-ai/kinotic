import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {KinoticProjectConfig} from '@kinotic-ai/management-api'
import {EntityCodeGenerationService} from '../../src/internal/EntityCodeGenerationService.js'
import {ConsoleLogger} from '../../src/internal/Logger.js'

const ENTITY_SOURCE = `import {Entity, Id} from '@kinotic-ai/persistence'

@Entity()
export class Todo {
    @Id()
    public id!: string
    public title!: string
}
`

const REPOSITORY_WITH_QUERY = `import {type IEntitiesRepository, Query} from '@kinotic-ai/persistence'
import {Todo} from '../model/Todo.js'
import {BaseTodoRepository} from './generated/BaseTodoRepository.js'

export class TodoRepository extends BaseTodoRepository {

  constructor(entitiesRepository?: IEntitiesRepository) {
    super(false, entitiesRepository)
  }

  @Query('SELECT * FROM todo WHERE title = :title')
  public async findByTitle(title: string): Promise<Todo[]> {
    throw new Error('not implemented')
  }

}
`

const TENANT_ENTITY_SOURCE = `import {Entity, Id, MultiTenancyType, TenantId} from '@kinotic-ai/persistence'

@Entity(MultiTenancyType.SHARED)
export class Todo {
    @Id()
    public id!: string
    public title!: string
    @TenantId
    public tenantId!: string
}
`

const ADMIN_REPOSITORY_WITH_TENANT_SELECTION = `import {type IAdminEntitiesRepository, Query, type TenantSelection} from '@kinotic-ai/persistence'
import {BaseTodoAdminRepository} from './generated/BaseTodoAdminRepository.js'

export class TodoAdminRepository extends BaseTodoAdminRepository {

  constructor(adminEntitiesRepository?: IAdminEntitiesRepository) {
    super(adminEntitiesRepository)
  }

  @Query('SELECT COUNT(title) AS count FROM todo WHERE title = :title')
  public async countByTitle(title: string, tenantSelection: TenantSelection): Promise<number> {
    throw new Error('not implemented')
  }

}
`

const ADMIN_REPOSITORY_WITHOUT_TENANT_SELECTION = ADMIN_REPOSITORY_WITH_TENANT_SELECTION
    .replace('title: string, tenantSelection: TenantSelection', 'title: string')

const REPOSITORY_WITH_TENANT_SELECTION = `import {type IEntitiesRepository, Query, type TenantSelection} from '@kinotic-ai/persistence'
import {BaseTodoRepository} from './generated/BaseTodoRepository.js'

export class TodoRepository extends BaseTodoRepository {

  constructor(entitiesRepository?: IEntitiesRepository) {
    super(false, entitiesRepository)
  }

  @Query('SELECT COUNT(title) AS count FROM todo WHERE title = :title')
  public async countByTitle(title: string, tenantSelection: TenantSelection): Promise<number> {
    throw new Error('not implemented')
  }

}
`

const REPOSITORY_WITHOUT_QUERY = `import {type IEntitiesRepository} from '@kinotic-ai/persistence'
import {BaseTodoRepository} from './generated/BaseTodoRepository.js'

export class TodoRepository extends BaseTodoRepository {

  constructor(entitiesRepository?: IEntitiesRepository) {
    super(false, entitiesRepository)
  }

}
`

const DATE_TIME_ENTITY_SOURCE = `import {DateTime, Entity, Id} from '@kinotic-ai/persistence'

export class Step {
    @DateTime
    public at!: string
}

@Entity()
export class Todo {
    @Id()
    public id!: string
    @DateTime
    public due!: string
    @DateTime
    public completed: string | null = null
    @DateTime
    public reminded?: string
    @DateTime
    public reminders: string[] = []
    @DateTime
    public holidays: readonly string[] = []
    public steps: Step[] = []
}
`

const DATE_TIME_ON_NUMBER_ENTITY_SOURCE = DATE_TIME_ENTITY_SOURCE.replace('public due!: string', 'public due!: number')

const DATE_TYPED_ENTITY_SOURCE = ENTITY_SOURCE.replace('public title!: string', 'public due: Date | null = null')

describe('EntityCodeGenerationService', () => {

    const projectConfig = new KinoticProjectConfig()
    let projectDir: string
    let originalCwd: string
    let logged: string[]

    // The service resolves the entities path, the tsconfig and .config against the working
    // directory, so each test runs from inside a throwaway project rather than the repo.
    beforeEach(() => {
        originalCwd = process.cwd()
        projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kinotic-gen-'))

        fs.mkdirSync(path.join(projectDir, 'src/model'), {recursive: true})
        fs.mkdirSync(path.join(projectDir, 'src/repository'), {recursive: true})
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), ENTITY_SOURCE)
        // Parameter types such as TenantSelection are recognized through the installed @kinotic-ai/persistence
        fs.symlinkSync(fileURLToPath(new URL('../../node_modules', import.meta.url)), path.join(projectDir, 'node_modules'))
        // No "files"/"include" on purpose: generation reads compilerOptions from the
        // tsconfig but must discover entities from entitiesPaths alone.
        // strict keeps nullable property types as unions, as they are in a real project.
        fs.writeFileSync(path.join(projectDir, 'tsconfig.json'), JSON.stringify({
            compilerOptions: {
                target: 'esnext',
                module: 'ES2020',
                moduleResolution: 'bundler',
                experimentalDecorators: true,
                skipLibCheck: true,
                strict: true
            },
            files: []
        }))
        logged = []

        projectConfig.applicationId = 'my.app'
        projectConfig.organizationId = 'acme'
        projectConfig.validate = false
        projectConfig.fileExtensionForImports = '.js'
        projectConfig.entitiesPaths = [{
            path: 'src/model',
            repositoryPath: 'src/repository',
            mirrorFolderStructure: false
        }]

        process.chdir(projectDir)
    })

    afterEach(() => {
        process.chdir(originalCwd)
        fs.rmSync(projectDir, {recursive: true, force: true})
    })

    // A fresh service per call, matching the one-generation-per-process lifecycle of `kinotic gen`.
    async function generate(): Promise<void> {
        // Conversion errors are reported through the logger, so the tests read them from there
        const logger = new ConsoleLogger()
        logger.log = (message?: string) => {
            logged.push(message ?? '')
        }
        const service = new EntityCodeGenerationService(projectConfig.applicationId,
                                                        projectConfig.fileExtensionForImports,
                                                        logger)
        await service.generateAllEntities(projectConfig, false, undefined, true)
    }

    async function generateError(): Promise<string | null> {
        let ret: string | null = null
        try {
            await generate()
        } catch (e) {
            ret = (e as Error).message
        }
        return ret
    }

    function readIfExists(...segments: string[]): string | null {
        const file = path.join(projectDir, ...segments)
        return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null
    }

    it('writes the entity C3Type json and the Repository classes without verbose', async () => {
        await generate()

        // The schemas are consumed server-side from the committed repository, so
        // generate must never leave a gitignore that hides them.
        expect(readIfExists('.config/c3/.gitignore'), 'c3 self-ignore').to.be.null

        const entityJson = readIfExists('.config/c3/entities/my.app.Todo.json')
        expect(entityJson, 'entity C3Type json').to.not.be.null

        const entity = JSON.parse(entityJson as string)
        expect(entity.name).to.equal('Todo')
        expect(entity.namespace).to.equal('my.app')
        expect(entity.properties.map((p: {name: string}) => p.name)).to.have.members(['id', 'title'])

        expect(readIfExists('src/repository/TodoRepository.ts'), 'Repository').to.not.be.null
        expect(readIfExists('src/repository/generated/BaseTodoRepository.ts'), 'Base Repository').to.not.be.null
    })

    it('discovers entities in nested folders and mirrors the folder structure', async () => {
        fs.mkdirSync(path.join(projectDir, 'src/model/billing'), {recursive: true})
        fs.writeFileSync(path.join(projectDir, 'src/model/billing/Invoice.ts'), ENTITY_SOURCE.replace(/Todo/g, 'Invoice'))
        projectConfig.entitiesPaths = [{
            path: 'src/model',
            repositoryPath: 'src/repository',
            mirrorFolderStructure: true
        }]

        await generate()

        expect(readIfExists('.config/c3/entities/my.app.Invoice.json'), 'nested entity C3Type json').to.not.be.null
        expect(readIfExists('src/repository/billing/InvoiceRepository.ts'), 'nested Repository').to.not.be.null
        expect(readIfExists('src/repository/billing/generated/BaseInvoiceRepository.ts'), 'nested Base Repository').to.not.be.null
    })

    it('writes the named queries json and removes it once the last query is deleted', async () => {
        // The first run generates the Repository the developer then declares queries on.
        await generate()

        fs.writeFileSync(path.join(projectDir, 'src/repository/TodoRepository.ts'), REPOSITORY_WITH_QUERY)
        await generate()

        const queriesJson = readIfExists('.config/c3/queries/TodoRepository.json')
        expect(queriesJson, 'named queries json').to.not.be.null

        const queries = JSON.parse(queriesJson as string)
        expect(queries.entityServiceName).to.equal('TodoRepository')
        expect(queries.namedQueries.map((q: {name: string}) => q.name)).to.deep.equal(['findByTitle'])

        fs.writeFileSync(path.join(projectDir, 'src/repository/TodoRepository.ts'), REPOSITORY_WITHOUT_QUERY)
        await generate()

        expect(readIfExists('.config/c3/queries/TodoRepository.json'), 'named queries json').to.be.null
    })

    it('passes an AdminRepository query tenant selection as the namedQuery argument', async () => {
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), TENANT_ENTITY_SOURCE)
        await generate()

        fs.writeFileSync(path.join(projectDir, 'src/repository/TodoAdminRepository.ts'), ADMIN_REPOSITORY_WITH_TENANT_SELECTION)
        await generate()

        const repository = readIfExists('src/repository/TodoAdminRepository.ts') as string
        expect(repository).to.contain(`{key: 'title', value: title}`)
        expect(repository).to.not.contain(`{key: 'tenantSelection'`)
        expect(repository).to.contain(`return this.namedQuery('countByTitle', parameters, tenantSelection)`)

        const queries = JSON.parse(readIfExists('.config/c3/queries/TodoAdminRepository.json') as string)
        expect(queries.namedQueries[0].parameters.map((p: {name: string}) => p.name)).to.deep.equal(['title', 'tenantSelection'])
    })

    it('fails an AdminRepository query without a tenant selection', async () => {
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), TENANT_ENTITY_SOURCE)
        await generate()

        fs.writeFileSync(path.join(projectDir, 'src/repository/TodoAdminRepository.ts'), ADMIN_REPOSITORY_WITHOUT_TENANT_SELECTION)

        expect(await generateError()).to.equal('TodoAdminRepository.countByTitle must declare a TenantSelection parameter')
    })

    it('fails a Repository query with a tenant selection', async () => {
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), TENANT_ENTITY_SOURCE)
        await generate()

        fs.writeFileSync(path.join(projectDir, 'src/repository/TodoRepository.ts'), REPOSITORY_WITH_TENANT_SELECTION)

        expect(await generateError())
            .to.equal('TodoRepository.countByTitle cannot declare a TenantSelection parameter, declare the query on the AdminRepository')
    })

    it('maps @DateTime string properties to date, in arrays and nested objects too', async () => {
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), DATE_TIME_ENTITY_SOURCE)
        await generate()

        const entity = JSON.parse(readIfExists('.config/c3/entities/my.app.Todo.json') as string)
        const types = Object.fromEntries(entity.properties.map((p: {name: string, type: unknown}) => [p.name, p.type]))
        expect(types.due).to.deep.equal({type: 'date'})
        expect(types.completed).to.deep.equal({type: 'date'})
        expect(types.reminded).to.deep.equal({type: 'date'})
        expect(types.reminders).to.deep.equal({type: 'array', contains: {type: 'date'}})
        expect(types.holidays).to.deep.equal({type: 'array', contains: {type: 'date'}})
        expect(types.steps.contains.properties).to.deep.equal([{name: 'at', type: {type: 'date'}}])
    })

    it('fails a property typed Date and logs how to declare it', async () => {
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), DATE_TYPED_ENTITY_SOURCE)

        expect(await generateError()).to.equal('Could not convert Todo to a C3Type')
        expect(logged.join('\n')).to.contain('The Date type is not supported')
                                 .and.to.contain('decorated with @DateTime')
                                 .and.to.contain('JsonPath: due')
    })

    it('fails @DateTime on a property that is not a string', async () => {
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), DATE_TIME_ON_NUMBER_ENTITY_SOURCE)

        expect(await generateError()).to.equal('Could not convert Todo to a C3Type')
        expect(logged.join('\n')).to.contain('@DateTime requires a string or string array property, but due is number')
    })

})
