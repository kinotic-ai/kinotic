import {isKinoticProject, loadKinoticProjectConfig} from '../internal/state/KinoticProjectConfigUtil.js'
import {FunctionDefinition, ObjectC3Type} from '@kinotic-ai/idl'
import { Kinotic } from '@kinotic-ai/core'
import {EntityDefinition,
        IEntityDefinitionService,
        INamedQueriesDefinitionService,
        NamedQueriesDefinition,
        OsApiPlugin,
        Project,
        ProjectType} from '@kinotic-ai/os-api'
import {Command, Flags} from '@oclif/core'
import chalk from 'chalk'
import {WebSocket} from 'ws'
import {CodeGenerationService} from '../internal/CodeGenerationService.js'
import {ProjectMigrationService} from '../internal/ProjectMigrationService.js'
import {resolveServer} from '../internal/state/Environment.js'
import {connectAndUpgradeSession} from '../internal/Utils.js'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})
Kinotic.use(OsApiPlugin)

export class Synchronize extends Command {
    static aliases = ['sync']

    static description = 'Synchronize the local Entity definitions with the Kinotic Server'

    static examples = [
        '$ kinotic synchronize',
        '$ kinotic sync',
        '$ kinotic synchronize --server http://localhost:9090 --publish --verbose',
        '$ kinotic sync -p -v -s http://localhost:9090'
    ]

    static flags = {
        server:     Flags.string({char: 's', description: 'The Kinotic server to connect to'}),
        publish:    Flags.boolean({char: 'p', description: 'Publish each Entity after save/update'}),
        verbose:    Flags.boolean({char: 'v', description: 'Enable verbose logging'}),
        authHeaderFile: Flags.string({char: 'f', description: 'JSON File containing authentication headers', required: false}),
        dryRun:     Flags.boolean({description: 'Dry run enables verbose logging and does not save any changes to the server'})
    }


    async run(): Promise<void> {
        const {flags} = await this.parse(Synchronize)

        try {

            if(!(await isKinoticProject())){
                this.error('The working directory is not a Kinotic Project')
            }

            const kinoticProjectConfig = await loadKinoticProjectConfig()

            let serverUrl = ''
            if(!flags.dryRun) {
                const serverConfig = await resolveServer(this.config.configDir, flags.server)
                serverUrl = serverConfig.url
            }

            if (flags.dryRun || await connectAndUpgradeSession(serverUrl, this, flags.authHeaderFile)) {
                try {

                    let project: Project | null = null
                    if(!flags.dryRun) {
                        await Kinotic.applications.createApplicationIfNotExist(kinoticProjectConfig.application, '')
                        project = new Project(null,
                                              kinoticProjectConfig.application,
                                              kinoticProjectConfig.name as string,
                                              kinoticProjectConfig.description)
                        project.sourceOfTruth = ProjectType.TYPESCRIPT
                        project = await Kinotic.projects.createProjectIfNotExist(project)
                    }

                    const codeGenerationService = new CodeGenerationService(kinoticProjectConfig.application,
                                                                            kinoticProjectConfig.fileExtensionForImports,
                                                                            this)

                    await codeGenerationService
                        .generateAllEntities(kinoticProjectConfig,
                                             flags.verbose || flags.dryRun,
                                             async (entityInfo, services) =>{

                                                 // combine named queries from generated services
                                                 const namedQueries: FunctionDefinition[] = []
                                                 for(let serviceInfo of services){
                                                     namedQueries.push(...serviceInfo.namedQueries)
                                                 }

                                                 // We sync named queries first since currently the backend cache eviction logic is a little dumb
                                                 // i.e. The cache eviction for the structure deletes the GraphQL schema
                                                 //      This will evict the named query execution plan cache
                                                 //      We want to make sure the GraphQL schema is updated after both these are updated and the structure below
                                                 if(!flags.dryRun && namedQueries.length > 0){
                                                     await this.synchronizeNamedQueries((project as Project).id as string, entityInfo.entity, namedQueries)
                                                 }

                                                 if(!flags.dryRun) {
                                                     await this.synchronizeEntity((project as Project).id as string, entityInfo.entity, flags.publish, flags.verbose)
                                                 }
                                             })

                    // Apply migrations after entity synchronization
                    if (!flags.dryRun) {
                        const migrationService = new ProjectMigrationService(this)
                        await migrationService.applyMigrations(
                            project!.id as string,
                            './migrations',
                            flags.verbose
                        )
                    }

                    this.log(`Synchronization Complete For application: ${kinoticProjectConfig.application}`)

                } catch (e) {
                    if (e instanceof Error) {
                        this.error(e.message)
                    }
                }
            }
            await Kinotic.disconnect()
        } catch (e) {
            if(e instanceof Error){
                this.log(chalk.red('Error: ') + e.message)
            }else{
                this.log(chalk.red('Error: ') + e as string)
            }
            await Kinotic.disconnect()
        }
        return
    }

    public logVerbose(message: string | ( () => string ), verbose: boolean): void {
        if (verbose) {
            if (typeof message === 'function') {
                this.log(message())
            }else{
                this.log(message)
            }
        }
    }

    private async synchronizeEntity(projectId: string,
                                    entity:  ObjectC3Type,
                                    publish: boolean,
                                    verbose: boolean): Promise<void> {
        const entityDefinitionService: IEntityDefinitionService = Kinotic.entityDefinitions
        const application = entity.namespace
        const name = entity.name
        const structureId = (application + '.' + name).toLowerCase()

        this.log(`Synchronizing Entity: ${application}.${name}`)

        try {
            let structure = await entityDefinitionService.findById(structureId)
            if (structure) {
                if (structure.published) {
                    this.log(chalk.bold(`Entity ${chalk.blue(application + '.' + name)} is Published. ${chalk.yellow('(Supported Modifications: New Fields. Un-Publish for all other changes.)')}`))
                }
                // update existing entity
                structure.entityDefinition = entity
                this.logVerbose(`Updating Entity: ${application}.${name}`, verbose)

                structure = await entityDefinitionService.save(structure)
            } else {
                structure = new EntityDefinition(application, projectId, name, entity)
                this.logVerbose(`Creating Entity: ${application}.${name}`, verbose)

                structure = await entityDefinitionService.create(structure)
            }
            // publish if we need to
            if(!structure.published && publish && structure?.id){
                this.logVerbose(`Publishing Entity: ${application}.${name}`, verbose)

                await entityDefinitionService.publish(structure.id)
            }
        } catch (e: any) {
            const message = e?.message || e
            this.log(chalk.red('Error') + ` Synchronizing Entity: ${structureId}, Exception: ${message}`)
        }
    }

    private async synchronizeNamedQueries(projectId: string,
                                          entity:       ObjectC3Type,
                                          namedQueries: FunctionDefinition[]): Promise<void> {
        const namedQueriesService: INamedQueriesDefinitionService = Kinotic.namedQueriesDefinitions
        const application = entity.namespace
        const structure = entity.name
        const id = (application + '.' + structure).toLowerCase()

        this.log(`Synchronizing Named Queries for Entity: ${application}.${structure}`)

        try {
            const namedQueriesDefinition = new NamedQueriesDefinition(id,
                                                                      application,
                                                                      projectId,
                                                                      structure,
                                                                      namedQueries)
            await namedQueriesService.save(namedQueriesDefinition)
        } catch (e: any) {
            const message = e?.message || e
            this.log(chalk.red('Error') + ` Synchronizing Named Queries for Entity: ${id}, Exception: ${message}`)
        }
    }
}


