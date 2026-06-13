import {Command, Flags} from '@oclif/core'
import fs from 'fs'
import path from 'path'
import chalk from 'chalk'
import { input } from '@inquirer/prompts'
import { isKinoticProject, saveKinoticProjectConfig } from '@/internal/state/KinoticProjectConfigUtil'
import { KinoticProjectConfig } from '@kinotic-ai/os-api'

/**
 * Validates the application name according to server requirements:
 * - First character must be a letter
 * - Can only contain letters, numbers, periods, underscores, or dashes
 */
function validateApplicationName(name: string): true | string {
    if (!name || name.length === 0) {
        return 'Application name cannot be empty'
    }

    // First character must be a letter
    if (!/^[a-zA-Z]/.test(name)) {
        return 'Application name must start with a letter'
    }

    // Can only contain letters, numbers, periods, underscores, or dashes
    if (!/^[a-zA-Z][a-zA-Z0-9._-]*$/.test(name)) {
        return 'Application name can only contain letters, numbers, periods, underscores, or dashes'
    }

    return true
}

export class Initialize extends Command {
    static aliases = ['init']

    static description = 'This will initialize a new Kinotic Project for use with the Kinotic CLI.'

    static examples = [
        '$ kinotic initialize --application my.app --entities path/to/entities --repository path/to/repository',
        '$ kinotic init --application my.app --entities path/to/entities --repository path/to/repository',
        '$ kinotic init -a my.app -e path/to/entities -r path/to/repository',
        '$ kinotic init -a my.app -e path/to/entities -r path/to/repository --mirror',
    ]

    static flags = {
        application:  Flags.string({char: 'a', description: 'The name of the application you want to use', required: false}),
        entities:   Flags.string({char: 'e', description: 'Path to the directory containing the Entity definitions', required: false}),
        repository: Flags.string({char: 'r', description: 'Path to the directory to write generated Repository classes', required: false}),
        mirror:     Flags.boolean({char: 'm', description: 'Mirror the entity folder structure under the repository path', default: true}),
    }

    public async run(): Promise<void> {
        const {flags} = await this.parse(Initialize)

        if(await isKinoticProject()){
            this.log(chalk.red('Error: ') + ' The working directory is already a Kinotic Project')
            return
        }

        // Prompt for missing values
        let application = flags.application
        if (!application) {
            application = await input({
                message: 'What is the name of your application?',
                validate: (input: string) => {
                    if (input.trim() === '') {
                        return 'Application name is required'
                    }
                    return validateApplicationName(input.trim())
                }
            })
        } else {
            // Validate provided application name from flag
            const validation = validateApplicationName(application)
            if (validation !== true) {
                this.error(validation)
            }
        }

        let entitiesPath = flags.entities
        if (!entitiesPath) {
            entitiesPath = await input({
                message: 'Path to the directory containing Entity definitions:',
                default: 'src/model',
                validate: (input: string) => input.trim() !== '' || 'Entities path is required'
            })
        }

        let repositoryPath = flags.repository
        if (!repositoryPath) {
            repositoryPath = await input({
                message: 'Path to the directory to write generated Repository classes:',
                default: 'src/repository',
                validate: (input: string) => input.trim() !== '' || 'Repository path is required'
            })
        }

        const entitiesAbsPath = path.resolve(entitiesPath)
        const repositoryAbsPath = path.resolve(repositoryPath)

        if(!fs.existsSync(entitiesAbsPath)){
            this.error(`Entities path does not exist: ${entitiesAbsPath}`)
        }
        if(!fs.existsSync(repositoryAbsPath)){
            this.error(`Repository path does not exist: ${repositoryAbsPath}`)
        }

        // Only use TypescriptProjectConfig for initialization
        const configDir = path.resolve(process.cwd(), '.config')
        const configObj = new KinoticProjectConfig()
        // Don't set name - it will be loaded from package.json
        configObj.application = application
        configObj.entitiesPaths = [{
            path: entitiesPath,
            repositoryPath: repositoryPath,
            mirrorFolderStructure: flags.mirror
        }]
        configObj.validate = false
        configObj.fileExtensionForImports = '.js'
        await saveKinoticProjectConfig(configObj, configDir)

        this.log(chalk.green('Success:') + ' Initialized Project')
    }
}
