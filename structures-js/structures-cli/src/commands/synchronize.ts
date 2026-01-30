import {Continuum} from '@kinotic/continuum-client'
import {FunctionDefinition, ObjectC3Type} from '@kinotic/continuum-idl'
import {INamedQueriesService,
        IStructureService,
        NamedQueriesDefinition,
        Project,
        ProjectType,
        Structure,
        Structures,
        TypescriptExternalProjectConfig,
        TypescriptProjectConfig} from '@kinotic/structures-api'
import {Args, Command, Flags} from '@oclif/core'
import chalk from 'chalk'
import {WebSocket} from 'ws'
import {CodeGenerationService} from '../internal/CodeGenerationService.js'
import {ProjectMigrationService} from '../internal/ProjectMigrationService.js'
import {resolveServer} from '../internal/state/Environment.js'
import {isStructuresProject, loadStructuresProjectConfig} from '../internal/state/StructuresProject.js'
import {connectAndUpgradeSession} from '../internal/Utils.js'

// This is required when running Continuum from node
Object.assign(global, { WebSocket})

export class Synchronize extends Command {
    static aliases = ['sync']

    static description = 'Synchronize the local Entity definitions with the Structures Server'

    static examples = [
        '$ structures synchronize',
        '$ structures sync',
        '$ structures synchronize --server http://localhost:9090 --publish --verbose',
        '$ structures sync -p -v -s http://localhost:9090'
    ]

    static flags = {
        server:     Flags.string({char: 's', description: 'The structures server to connect to'}),
        publish:    Flags.boolean({char: 'p', description: 'Publish each Entity after save/update'}),
        verbose:    Flags.boolean({char: 'v', description: 'Enable verbose logging'}),
        authHeaderFile: Flags.string({char: 'f', description: 'JSON File containing authentication headers', required: false}),
        dryRun:     Flags.boolean({description: 'Dry run enables verbose logging and does not save any changes to the server'})
    }


    async run(): Promise<void> {
        const {flags} = await this.parse(Synchronize)

        try {

            if(!(await isStructuresProject())){
                this.error('The working directory is not a Structures Project')
            }

            const structuresProjectConfig = await loadStructuresProjectConfig()

            let serverUrl = ''
            if(!flags.dryRun) {
                const serverConfig = await resolveServer(this.config.configDir, flags.server)
                serverUrl = serverConfig.url
            }

            if (flags.dryRun || await connectAndUpgradeSession(serverUrl, this, flags.authHeaderFile)) {
                try {

                    let project: Project | null = null
                    if(!flags.dryRun) {
                        await Structures.getApplicationService().createApplicationIfNotExist(structuresProjectConfig.application, '')
                        project = new Project(null,
                                              structuresProjectConfig.application,
                                              structuresProjectConfig.name as string,
                                              structuresProjectConfig.description)
                        project.sourceOfTruth = ProjectType.TYPESCRIPT
                        project = await Structures.getProjectService().createProjectIfNotExist(project)
                    }

                    const codeGenerationService = new CodeGenerationService(structuresProjectConfig.application,
                                                                            structuresProjectConfig.fileExtensionForImports,
                                                                            this)

                    await codeGenerationService
                        .generateAllEntities(structuresProjectConfig as TypescriptProjectConfig | TypescriptExternalProjectConfig,
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

                    this.log(`Synchronization Complete For application: ${structuresProjectConfig.application}`)

                } catch (e) {
                    if (e instanceof Error) {
                        this.error(e.message)
                    }
                }
            }
            await Continuum.disconnect()
        } catch (e) {
            if(e instanceof Error){
                this.log(chalk.red('Error: ') + e.message)
            }else{
                this.log(chalk.red('Error: ') + e as string)
            }
            await Continuum.disconnect()
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
        const structureService: IStructureService = Structures.getStructureService()
        const application = entity.namespace
        const name = entity.name
        const structureId = (application + '.' + name).toLowerCase()

        this.log(`Synchronizing Structure: ${application}.${name}`)

        try {
            let structure = await structureService.findById(structureId)
            if (structure) {
                if (structure.published) {
                    this.log(chalk.bold(`Structure ${chalk.blue(application + '.' + name)} is Published. ${chalk.yellow('(Supported Modifications: New Fields. Un-Publish for all other changes.)')}`))
                }
                // update existing structure
                structure.entityDefinition = entity
                this.logVerbose(`Updating Structure: ${application}.${name}`, verbose)

                structure = await structureService.save(structure)
            } else {
                structure = new Structure(application, projectId, name, entity)
                this.logVerbose(`Creating Structure: ${application}.${name}`, verbose)

                structure = await structureService.create(structure)
            }
            // publish if we need to
            if(!structure.published && publish && structure?.id){
                this.logVerbose(`Publishing Structure: ${application}.${name}`, verbose)

                await structureService.publish(structure.id)
            }
        } catch (e: any) {
            const message = e?.message || e
            this.log(chalk.red('Error') + ` Synchronizing Structure: ${structureId}, Exception: ${message}`)
        }
    }

    private async synchronizeNamedQueries(projectId: string,
                                          entity:       ObjectC3Type,
                                          namedQueries: FunctionDefinition[]): Promise<void> {
        const namedQueriesService: INamedQueriesService = Structures.getNamedQueriesService()
        const application = entity.namespace
        const structure = entity.name
        const id = (application + '.' + structure).toLowerCase()

        this.log(`Synchronizing Named Queries for Structure: ${application}.${structure}`)

        try {
            const namedQueriesDefinition = new NamedQueriesDefinition(id,
                                                                      application,
                                                                      projectId,
                                                                      structure,
                                                                      namedQueries)
            await namedQueriesService.save(namedQueriesDefinition)
        } catch (e: any) {
            const message = e?.message || e
            this.log(chalk.red('Error') + ` Synchronizing Named Queries for Structure: ${id}, Exception: ${message}`)
        }
    }
}


