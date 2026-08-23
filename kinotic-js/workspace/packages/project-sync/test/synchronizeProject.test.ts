import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { readC3Definitions, synchronizeProject, type SyncLogger } from '@/index'

class RecordingLogger implements SyncLogger {
    readonly lines: string[] = []

    log(message: string): void {
        this.lines.push(message)
    }

    logVerbose(message: string | (() => string), verbose: boolean): void {
        if (verbose) {
            this.log(typeof message === 'function' ? message() : message)
        }
    }
}

const TODO_ENTITY = {
    name: 'Todo',
    namespace: 'my.app',
    properties: [{ name: 'id' }, { name: 'title' }],
}

const TODO_QUERIES = {
    entityServiceName: 'TodoRepository',
    entityNamespace: 'my.app',
    entityName: 'Todo',
    namedQueries: [{ name: 'findByTitle' }],
}

describe('project-sync', () => {

    let projectDir: string
    let logger: RecordingLogger

    beforeEach(() => {
        projectDir = join(tmpdir(), `project-sync-${crypto.randomUUID()}`)
        mkdirSync(join(projectDir, '.config', 'c3', 'entities'), { recursive: true })
        mkdirSync(join(projectDir, '.config', 'c3', 'queries'), { recursive: true })
        logger = new RecordingLogger()
    })

    afterEach(() => {
        rmSync(projectDir, { recursive: true, force: true })
    })

    function write(relative: string, value: unknown): void {
        writeFileSync(join(projectDir, relative), JSON.stringify(value))
    }

    it('reads the committed entity and named-query definitions', () => {
        write('.config/c3/entities/my.app.Todo.json', TODO_ENTITY)
        write('.config/c3/queries/TodoRepository.json', TODO_QUERIES)

        const definitions = readC3Definitions(projectDir, logger)

        expect(definitions.entities.map(e => e.name)).toEqual(['Todo'])
        expect(definitions.queries[0]?.entityName).toBe('Todo')
        expect(definitions.queries[0]?.namedQueries.map(q => q.name)).toEqual(['findByTitle'])
    })

    it('reads an empty result when the project has no c3 directory', () => {
        rmSync(join(projectDir, '.config'), { recursive: true })

        const definitions = readC3Definitions(projectDir, logger)

        expect(definitions.entities).toEqual([])
        expect(definitions.queries).toEqual([])
    })

    it('rejects an entity definition without a name', () => {
        write('.config/c3/entities/broken.json', { properties: [] })

        expect(() => readC3Definitions(projectDir, logger)).toThrow(/no name\/namespace/)
    })

    it('skips a queries file that predates the entity pairing fields', () => {
        write('.config/c3/queries/TodoRepository.json',
              { entityServiceName: 'TodoRepository', namedQueries: [{ name: 'findByTitle' }] })

        const definitions = readC3Definitions(projectDir, logger)

        expect(definitions.queries).toEqual([])
        expect(logger.lines.join('\n')).toContain('regenerate with a newer CLI')
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

        expect(logger.lines.join('\n')).toContain('Would synchronize entity: my.app.Todo')
        expect(logger.lines.join('\n')).toContain('Would synchronize named queries: TodoRepository')
    })

    it('requires the project identity fields', async () => {
        await expect(synchronizeProject({ organizationId: '', applicationId: 'a', projectName: 'p', projectDir }))
            .rejects.toThrow(/organizationId is required/)
    })
})
