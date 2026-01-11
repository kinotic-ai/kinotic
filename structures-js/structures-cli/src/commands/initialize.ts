import {Command, Flags} from '@oclif/core'
import fs from 'fs'
import path from 'path'
import chalk from 'chalk'
import { input } from '@inquirer/prompts'
import { isStructuresProject, saveStructuresProjectConfig } from '../internal/state/StructuresProject.js'
import { TypescriptProjectConfig } from '@mindignited/structures-api'

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

    static description = 'This will initialize a new Structures Project for use with the Structures CLI.'

    static examples = [
        '$ structures initialize --application my.app --entities path/to/entities --generated path/to/services',
        '$ structures init --application my.app --entities path/to/entities --generated path/to/services',
        '$ structures init -a my.app -e path/to/entities -g path/to/services',
    ]

    static flags = {
        application:  Flags.string({char: 'a', description: 'The name of the application you want to use', required: false}),
        entities:   Flags.string({char: 'e', description: 'Path to the directory containing the Entity definitions', required: false}),
        generated:  Flags.string({char: 'g', description: 'Path to the directory to write generated Services', required: false}),
    }

    public async run(): Promise<void> {
        const {flags} = await this.parse(Initialize)

        if(await isStructuresProject()){
            this.log(chalk.red('Error: ') + ' The working directory is already a Structures Project')
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
                default: 'src/entities',
                validate: (input: string) => input.trim() !== '' || 'Entities path is required'
            })
        }

        let generatedPath = flags.generated
        if (!generatedPath) {
            generatedPath = await input({
                message: 'Path to the directory to write generated Services:',
                default: 'src/generated',
                validate: (input: string) => input.trim() !== '' || 'Generated path is required'
            })
        }

        const entitiesAbsPath = path.resolve(entitiesPath)
        const generatedAbsPath = path.resolve(generatedPath)

        if(!fs.existsSync(entitiesAbsPath)){
            this.error(`Entities path does not exist: ${entitiesAbsPath}`)
        }
        if(!fs.existsSync(generatedAbsPath)){
            this.error(`Generated path does not exist: ${generatedAbsPath}`)
        }

        // Only use TypescriptProjectConfig for initialization
        const configDir = path.resolve(process.cwd(), '.config')
        const configObj = new TypescriptProjectConfig()
        // Don't set name - it will be loaded from package.json
        configObj.application = application
        configObj.entitiesPaths = [entitiesPath]
        configObj.generatedPath = generatedPath
        configObj.validate = false
        configObj.fileExtensionForImports = '.js'
        await saveStructuresProjectConfig(configObj, configDir)

        this.log(chalk.green('Success:') + ' Initialized Project')
    }
}
