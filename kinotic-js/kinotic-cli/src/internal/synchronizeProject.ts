import { Kinotic } from '@kinotic-ai/core'
import type { FunctionDefinition, ObjectC3Type } from '@kinotic-ai/idl'
import { EntityDefinition, NamedQueriesDefinition, Project, ProjectType } from '@kinotic-ai/os-api'
import { join } from 'node:path'
import { readC3Definitions } from './C3Definitions'
import { ConsoleLogger, type Logger } from './Logger'
import { ProjectMigrationService } from './ProjectMigrationService'

/** The subset of {@link Logger} synchronization reports progress through. */
type SyncLogger = Pick<Logger, 'log' | 'logVerbose'>

export interface SynchronizeProjectOptions {
    organizationId: string
    applicationId: string
    projectName: string
    projectDescription?: string
    /** Root of the project checkout holding `.config/c3` and `migrations`; defaults to the working directory. */
    projectDir?: string
    /** Publish each entity definition after save when it is not published yet. */
    publish?: boolean
    /** Report what would be synchronized without contacting the server. */
    dryRun?: boolean
    verbose?: boolean
    logger?: SyncLogger
}

/**
 * Synchronizes a project's committed definitions with the Kinotic server: ensures the
 * application and project exist, pushes every entity and named-query definition found
 * under `.config/c3`, then applies the project's pending migrations.
 *
 * The caller must already be connected ({@code Kinotic.connect()}) with a participant
 * authorized for the organization — any credential mechanism works. Definitions come from
 * the committed output of `kinotic generate`; no code generation happens here.
 */
export async function synchronizeProject(options: SynchronizeProjectOptions): Promise<void> {
    for (const field of ['organizationId', 'applicationId', 'projectName'] as const) {
        if (!options[field]) {
            throw new Error(`${field} is required`)
        }
    }
    const logger = options.logger ?? new ConsoleLogger()
    const projectDir = options.projectDir ?? process.cwd()
    const verbose = options.verbose ?? false

    const definitions = readC3Definitions(projectDir, logger)
    if (definitions.entities.length === 0) {
        logger.log('No entity definitions found under .config/c3 — run `kinotic generate` and commit the result')
    }

    if (options.dryRun) {
        for (const entity of definitions.entities) {
            logger.log(`Would synchronize entity: ${entity.namespace}.${entity.name}`)
        }
        for (const queries of definitions.queries) {
            logger.log(`Would synchronize named queries: ${queries.entityServiceName} `
                       + `(entity ${queries.entityNamespace}.${queries.entityName})`)
        }
        return
    }

    await Kinotic.applications.createApplicationIfNotExist(options.applicationId, '')
    let project = new Project(null, options.applicationId, options.projectName, options.projectDescription ?? '')
    project.organizationId = options.organizationId
    project.sourceOfTruth = ProjectType.TYPESCRIPT
    project = await Kinotic.projects.createProjectIfNotExist(project)
    const projectId = project.id as string

    // A Repository's queries pair with its entity; several Repositories may serve one entity
    const queriesByEntity = new Map<string, FunctionDefinition[]>()
    for (const queries of definitions.queries) {
        const key = `${queries.entityNamespace}.${queries.entityName}`
        queriesByEntity.set(key, [...(queriesByEntity.get(key) ?? []), ...queries.namedQueries])
    }
    const entityKeys = new Set(definitions.entities.map(entity => `${entity.namespace}.${entity.name}`))
    for (const key of queriesByEntity.keys()) {
        if (!entityKeys.has(key)) {
            logger.log(`Warning: named queries reference entity ${key}, which has no committed definition; skipping them`)
        }
    }

    const failed: string[] = []
    for (const entity of definitions.entities) {
        const key = `${entity.namespace}.${entity.name}`
        try {
            // Named queries go first: the entity save evicts the GraphQL schema, which must
            // be rebuilt only after both the queries and the entity are updated
            const namedQueries = queriesByEntity.get(key) ?? []
            if (namedQueries.length > 0) {
                await synchronizeNamedQueries(options.organizationId, projectId, entity, namedQueries, logger)
            }
            await synchronizeEntity(options.organizationId, projectId, entity,
                                    options.publish ?? false, verbose, logger)
        } catch (e) {
            logger.log(`Error synchronizing ${key}: ${e instanceof Error ? e.message : e}`)
            failed.push(key)
        }
    }
    if (failed.length > 0) {
        throw new Error(`Failed to synchronize: ${failed.join(', ')}`)
    }

    await new ProjectMigrationService(logger).applyMigrations(projectId, join(projectDir, 'migrations'), verbose)

    logger.log(`Synchronization complete for application: ${options.applicationId}`)
}

async function synchronizeEntity(organizationId: string,
                                 projectId: string,
                                 entitySchema: ObjectC3Type,
                                 publish: boolean,
                                 verbose: boolean,
                                 logger: SyncLogger): Promise<void> {
    const application = entitySchema.namespace
    const name = entitySchema.name
    const entityDefinitionId = (organizationId + '.' + application + '.' + name).toLowerCase()

    logger.log(`Synchronizing Entity: ${application}.${name}`)

    let entityDefinition = await Kinotic.entityDefinitions.findById(entityDefinitionId)
    if (entityDefinition) {
        if (entityDefinition.published) {
            logger.log(`Entity ${application}.${name} is Published. `
                       + '(Supported Modifications: New Fields. Un-Publish for all other changes.)')
        }
        entityDefinition.schema = entitySchema
        logger.logVerbose(`Updating Entity: ${application}.${name}`, verbose)
        entityDefinition = await Kinotic.entityDefinitions.save(entityDefinition)
    } else {
        entityDefinition = new EntityDefinition(organizationId, application, projectId, name, entitySchema)
        logger.logVerbose(`Creating Entity: ${application}.${name}`, verbose)
        entityDefinition = await Kinotic.entityDefinitions.create(entityDefinition)
    }

    if (!entityDefinition.published && publish && entityDefinition?.id) {
        logger.logVerbose(`Publishing Entity: ${application}.${name}`, verbose)
        await Kinotic.entityDefinitions.publish(entityDefinition.id)
    }
}

async function synchronizeNamedQueries(organizationId: string,
                                       projectId: string,
                                       entitySchema: ObjectC3Type,
                                       namedQueries: FunctionDefinition[],
                                       logger: SyncLogger): Promise<void> {
    const application = entitySchema.namespace
    const entityDefinitionName = entitySchema.name
    const id = (organizationId + '.' + application + '.' + entityDefinitionName).toLowerCase()

    logger.log(`Synchronizing Named Queries for Entity: ${application}.${entityDefinitionName}`)

    const namedQueriesDefinition = new NamedQueriesDefinition(id,
                                                              organizationId,
                                                              application,
                                                              projectId,
                                                              entityDefinitionName,
                                                              namedQueries)
    await Kinotic.namedQueriesDefinitions.save(namedQueriesDefinition)
}
