import {chdirToProjectRoot, isKinoticProject, loadKinoticProjectConfig} from '@/internal/state/KinoticProjectConfigUtil'
import { Kinotic } from '@kinotic-ai/core'
import { OsApiPlugin } from '@kinotic-ai/os-api'
import {Command, Flags} from '@oclif/core'
import chalk from 'chalk'
import {EntityCodeGenerationService} from '@/internal/EntityCodeGenerationService'
import {synchronizeProject} from '@/internal/synchronizeProject'
import {resolveServer} from '@/internal/state/Environment'
import {CliAuthenticator} from '@/internal/CliAuthenticator'

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
        dryRun:     Flags.boolean({description: 'Dry run enables verbose logging and does not save any changes to the server'}),
        force:      Flags.boolean({description: 'Force full regeneration, ignoring incremental change detection', default: false})
    }


    async run(): Promise<void> {
        const {flags} = await this.parse(Synchronize)

        try {

            if(!(await isKinoticProject())){
                this.error('The working directory is not a Kinotic Project')
            }
            chdirToProjectRoot()

            const kinoticProjectConfig = await loadKinoticProjectConfig()

            // Generation runs first as the build step: it compiles the entity sources and
            // refreshes the committed .config/c3 definitions synchronization reads below,
            // so a project that does not build never reaches the server
            const codeGenerationService = new EntityCodeGenerationService(kinoticProjectConfig.applicationId,
                                                                          kinoticProjectConfig.fileExtensionForImports,
                                                                          this)
            await codeGenerationService.generateAllEntities(kinoticProjectConfig,
                                                            flags.verbose || flags.dryRun,
                                                            flags.force)

            if(!flags.dryRun) {
                const serverConfig = await resolveServer(this.config.configDir, flags.server)
                if (!(await new CliAuthenticator(serverConfig.url, this.config.configDir, this).connect())) {
                    this.error('Could not connect to the Kinotic Server')
                }
            }

            try {
                await synchronizeProject({
                    organizationId: kinoticProjectConfig.organizationId,
                    applicationId: kinoticProjectConfig.applicationId,
                    projectName: kinoticProjectConfig.name as string,
                    projectDescription: kinoticProjectConfig.description,
                    publish: flags.publish,
                    dryRun: flags.dryRun,
                    verbose: flags.verbose,
                    logger: this
                })
            } catch (e) {
                if (e instanceof Error) {
                    this.error(e.message)
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
}
