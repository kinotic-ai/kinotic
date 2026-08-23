import type { FunctionDefinition, ObjectC3Type } from '@kinotic-ai/idl'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { SyncLogger } from './SyncLogger'

/**
 * Named queries of one generated Repository, as `kinotic generate` writes them to
 * `.config/c3/queries/<Repository>.json`. The entity fields pair the file with the entity
 * definition the Repository serves.
 */
export interface C3NamedQueries {
    entityServiceName: string
    entityNamespace: string
    entityName: string
    namedQueries: FunctionDefinition[]
}

/**
 * The committed contents of a project's `.config/c3` directory: every entity C3Type from
 * `entities/` and every Repository's named queries from `queries/`.
 */
export interface C3Definitions {
    entities: ObjectC3Type[]
    queries: C3NamedQueries[]
}

function readJsonFiles(directory: string): Array<{ file: string, value: unknown }> {
    let ret: Array<{ file: string, value: unknown }> = []
    if (existsSync(directory)) {
        ret = readdirSync(directory)
            .filter(file => file.endsWith('.json'))
            .sort()
            .map(file => ({ file, value: JSON.parse(readFileSync(join(directory, file), 'utf-8')) }))
    }
    return ret
}

/**
 * Reads the entity and named-query definitions `kinotic generate` committed under the
 * project's `.config/c3` directory. Queries files missing the entity pairing fields (from a
 * generator predating them) are skipped with a warning telling the developer to regenerate.
 */
export function readC3Definitions(projectDir: string, logger: SyncLogger): C3Definitions {
    const c3Dir = join(projectDir, '.config', 'c3')

    const entities: ObjectC3Type[] = []
    for (const { file, value } of readJsonFiles(join(c3Dir, 'entities'))) {
        const entity = value as ObjectC3Type
        if (!entity.name || !entity.namespace) {
            throw new Error(`Entity definition ${file} has no name/namespace — regenerate with \`kinotic generate\``)
        }
        entities.push(entity)
    }

    const queries: C3NamedQueries[] = []
    for (const { file, value } of readJsonFiles(join(c3Dir, 'queries'))) {
        const namedQueries = value as C3NamedQueries
        if (!namedQueries.entityNamespace || !namedQueries.entityName) {
            logger.log(`Warning: ${file} does not name the entity it serves; `
                       + 'regenerate with a newer CLI (`kinotic generate`) to sync its named queries')
            continue
        }
        queries.push(namedQueries)
    }

    return { entities, queries }
}
