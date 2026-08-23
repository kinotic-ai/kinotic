import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {KinoticProjectConfig} from '@kinotic-ai/os-api'
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

const REPOSITORY_WITHOUT_QUERY = `import {type IEntitiesRepository} from '@kinotic-ai/persistence'
import {BaseTodoRepository} from './generated/BaseTodoRepository.js'

export class TodoRepository extends BaseTodoRepository {

  constructor(entitiesRepository?: IEntitiesRepository) {
    super(false, entitiesRepository)
  }

}
`

describe('EntityCodeGenerationService', () => {

    const projectConfig = new KinoticProjectConfig()
    let projectDir: string
    let originalCwd: string

    // The service resolves the entities path, the tsconfig and .config against the working
    // directory, so each test runs from inside a throwaway project rather than the repo.
    beforeEach(() => {
        originalCwd = process.cwd()
        projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kinotic-gen-'))

        fs.mkdirSync(path.join(projectDir, 'src/model'), {recursive: true})
        fs.mkdirSync(path.join(projectDir, 'src/repository'), {recursive: true})
        fs.writeFileSync(path.join(projectDir, 'src/model/Todo.ts'), ENTITY_SOURCE)
        // No "files"/"include" on purpose: generation reads compilerOptions from the
        // tsconfig but must discover entities from entitiesPaths alone.
        fs.writeFileSync(path.join(projectDir, 'tsconfig.json'), JSON.stringify({
            compilerOptions: {
                target: 'esnext',
                module: 'ES2020',
                moduleResolution: 'bundler',
                experimentalDecorators: true,
                skipLibCheck: true
            },
            files: []
        }))

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
        const service = new EntityCodeGenerationService(projectConfig.applicationId,
                                                        projectConfig.fileExtensionForImports,
                                                        new ConsoleLogger())
        await service.generateAllEntities(projectConfig, false, true)
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
        // The entity pairing is what synchronization uses to match the file to its entity json
        expect(queries.entityNamespace).to.equal('my.app')
        expect(queries.entityName).to.equal('Todo')
        expect(queries.namedQueries.map((q: {name: string}) => q.name)).to.deep.equal(['findByTitle'])

        fs.writeFileSync(path.join(projectDir, 'src/repository/TodoRepository.ts'), REPOSITORY_WITHOUT_QUERY)
        await generate()

        expect(readIfExists('.config/c3/queries/TodoRepository.json'), 'named queries json').to.be.null
    })

})
