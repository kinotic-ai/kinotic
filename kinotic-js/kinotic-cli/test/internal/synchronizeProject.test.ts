import {expect} from 'chai'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import crypto from 'node:crypto'
import {readC3Definitions} from '../../src/internal/C3Definitions.js'
import {synchronizeProject} from '../../src/internal/synchronizeProject.js'
import {ConsoleLogger} from '../../src/internal/Logger.js'

class RecordingLogger extends ConsoleLogger {
    readonly lines: string[] = []

    override log(message?: string): void {
        this.lines.push(message ?? '')
    }
}

const TODO_ENTITY = {
    name: 'Todo',
    namespace: 'my.app',
    properties: [{name: 'id'}, {name: 'title'}],
}

const TODO_QUERIES = {
    entityServiceName: 'TodoRepository',
    entityNamespace: 'my.app',
    entityName: 'Todo',
    namedQueries: [{name: 'findByTitle'}],
}

describe('synchronizeProject', () => {

    let projectDir: string
    let logger: RecordingLogger

    beforeEach(() => {
        projectDir = fs.mkdtempSync(path.join(os.tmpdir(), `kinotic-sync-${crypto.randomUUID()}`))
        fs.mkdirSync(path.join(projectDir, '.config/c3/entities'), {recursive: true})
        fs.mkdirSync(path.join(projectDir, '.config/c3/queries'), {recursive: true})
        logger = new RecordingLogger()
    })

    afterEach(() => {
        fs.rmSync(projectDir, {recursive: true, force: true})
    })

    function write(relative: string, value: unknown): void {
        fs.writeFileSync(path.join(projectDir, relative), JSON.stringify(value))
    }

    it('reads the committed entity and named-query definitions', () => {
        write('.config/c3/entities/my.app.Todo.json', TODO_ENTITY)
        write('.config/c3/queries/TodoRepository.json', TODO_QUERIES)

        const definitions = readC3Definitions(projectDir, logger)

        expect(definitions.entities.map(e => e.name)).to.deep.equal(['Todo'])
        expect(definitions.queries[0].entityName).to.equal('Todo')
        expect(definitions.queries[0].namedQueries.map(q => q.name)).to.deep.equal(['findByTitle'])
    })

    it('reads an empty result when the project has no c3 directory', () => {
        fs.rmSync(path.join(projectDir, '.config'), {recursive: true})

        const definitions = readC3Definitions(projectDir, logger)

        expect(definitions.entities).to.deep.equal([])
        expect(definitions.queries).to.deep.equal([])
    })

    it('rejects an entity definition without a name', () => {
        write('.config/c3/entities/broken.json', {properties: []})

        expect(() => readC3Definitions(projectDir, logger)).to.throw(/no name\/namespace/)
    })

    it('skips a queries file that predates the entity pairing fields', () => {
        write('.config/c3/queries/TodoRepository.json',
              {entityServiceName: 'TodoRepository', namedQueries: [{name: 'findByTitle'}]})

        const definitions = readC3Definitions(projectDir, logger)

        expect(definitions.queries).to.deep.equal([])
        expect(logger.lines.join('\n')).to.contain('regenerate with a newer CLI')
    })

    it('dry run reports what would be synchronized without a server connection', async () => {
        write('.config/c3/entities/my.app.Todo.json', TODO_ENTITY)
        write('.config/c3/queries/TodoRepository.json', TODO_QUERIES)

        await synchronizeProject({
            organizationId: 'acme',
            applicationId: 'my.app',
            projectName: 'todo',
            projectDir,
            dryRun: true,
            logger,
        })

        expect(logger.lines.join('\n')).to.contain('Would synchronize entity: my.app.Todo')
        expect(logger.lines.join('\n')).to.contain('Would synchronize named queries: TodoRepository')
    })

    it('requires the project identity fields', async () => {
        let failure: Error | null = null
        try {
            await synchronizeProject({organizationId: '', applicationId: 'a', projectName: 'p', projectDir})
        } catch (e) {
            failure = e as Error
        }
        expect(failure?.message).to.contain('organizationId is required')
    })
})
