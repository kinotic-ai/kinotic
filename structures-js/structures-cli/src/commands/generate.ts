import {CodeGenerationService} from '../internal/CodeGenerationService.js'
import {Args, Command, Flags} from '@oclif/core'
import {
    isStructuresProject,
    loadStructuresProjectConfig
} from '../internal/state/StructuresProject.js'
import { TypescriptExternalProjectConfig, TypescriptProjectConfig } from '@kinotic/structures-api'

export class Generate extends Command {
    static aliases = ['gen']

    static description = 'This will generate all Entity Service classes.'

    static examples = [
        '$ structures generate',
        '$ structures gen',
        '$ structures gen -v',
    ]

    static flags = {
        verbose:    Flags.boolean({char: 'v', description: 'Enable verbose logging', default: false}),
    }

    public async run(): Promise<void> {
        const {flags} = await this.parse(Generate)

        if(!(await isStructuresProject())){
            this.error('The working directory is not a Structures Project')
        }

        const structuresProjectConfig = await loadStructuresProjectConfig()

        const codeGenerationService = new CodeGenerationService(structuresProjectConfig.application,
                                                                structuresProjectConfig.fileExtensionForImports,
                                                                this)

            await codeGenerationService.generateAllEntities(structuresProjectConfig as TypescriptProjectConfig | TypescriptExternalProjectConfig, flags.verbose)

        this.log(`Code Generation Complete For application: ${structuresProjectConfig.application}`)
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
