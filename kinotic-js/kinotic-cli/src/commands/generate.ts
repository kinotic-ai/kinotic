import {EntityCodeGenerationService} from '@/internal/EntityCodeGenerationService'
import {Command, Flags} from '@oclif/core'
import {
    isKinoticProject,
    loadKinoticProjectConfig
} from '@/internal/state/KinoticProjectConfigUtil'

export class Generate extends Command {
    static aliases = ['gen']

    static description = 'This will generate all Repository classes.'

    static examples = [
        '$ kinotic generate',
        '$ kinotic gen',
        '$ kinotic gen -v',
        '$ kinotic gen --force',
    ]

    static flags = {
        verbose:    Flags.boolean({char: 'v', description: 'Enable verbose logging', default: false}),
        force:      Flags.boolean({char: 'f', description: 'Force full regeneration, ignoring incremental change detection', default: false}),
    }

    public async run(): Promise<void> {
        const {flags} = await this.parse(Generate)

        if(!(await isKinoticProject())){
            this.error('The working directory is not a Kinotic Project')
        }

        const kinoticProjectConfig = await loadKinoticProjectConfig()

        const codeGenerationService = new EntityCodeGenerationService(kinoticProjectConfig.application,
                                                                      kinoticProjectConfig.fileExtensionForImports,
                                                                      this)

            await codeGenerationService.generateAllEntities(kinoticProjectConfig, flags.verbose, undefined, flags.force)

        this.log(`Code Generation Complete For application: ${kinoticProjectConfig.application}`)
    }

    // This is needed for the CodeGenerationService to log verbose messages
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
