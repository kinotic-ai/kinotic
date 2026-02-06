import fs from 'fs/promises'
import path from 'path'
import { ObjectC3Type } from '@kinotic/continuum-idl'
import { ConsoleLogger } from '@kinotic/structures-cli/dist/internal/Logger.js'
import { EntityDefinitionGenerator } from './EntityDefinitionGenerator'

// Use __dirname to ensure we're looking in the right place relative to this file
const PROD_JSON_DIR = path.resolve(__dirname, 'entity-definitions')

export class EntityDefinitionLoader {
    private readonly logger: ConsoleLogger
    private generator?: EntityDefinitionGenerator

    constructor(
        private readonly namespace: string,
        private readonly entitiesPath: string,
        private readonly generatedPath: string
    ) {
        this.logger = new ConsoleLogger()
    }

    async loadDefinitions(): Promise<Map<string, ObjectC3Type>> {
        if (process.env.NODE_ENV === 'production') {
            return this.loadFromJson()
        } else {
            this.logger.log('Development environment - generating entity definitions from source')
            if (!this.generator) {
                this.generator = new EntityDefinitionGenerator(
                    this.namespace,
                    this.entitiesPath,
                    this.generatedPath
                )
            }
            return this.generator.generateDefinitions()
        }
    }

    private async loadFromJson(): Promise<Map<string, ObjectC3Type>> {
        const definitions = new Map<string, ObjectC3Type>()

        try {
            const files = await fs.readdir(PROD_JSON_DIR)
            const jsonFiles = files.filter(file => file.endsWith('.json'))

            if (jsonFiles.length === 0) {
                throw new Error('No entity definition JSON files found')
            }

            for (const file of jsonFiles) {
                const filePath = path.join(PROD_JSON_DIR, file)
                const content = await fs.readFile(filePath, 'utf-8')
                const definition = JSON.parse(content) as ObjectC3Type
                definitions.set(definition.name.toLowerCase(), definition)
            }

            this.logger.log(`Loaded ${definitions.size} entity definitions from JSON files for ${this.namespace}`)
        } catch (error) {
            this.logger.log(`Error loading JSON files: ${error}`)
            throw new Error('Failed to load entity definitions from JSON files in production environment')
        }

        return definitions
    }
} 