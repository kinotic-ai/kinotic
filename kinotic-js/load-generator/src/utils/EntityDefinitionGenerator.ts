import { ObjectC3Type } from '@kinotic-ai/idl'
import { ConsoleLogger } from '@kinotic-ai/kinotic-cli/dist/internal/Logger.js'
import { CodeGenerationService } from '@kinotic-ai/kinotic-cli/dist/internal/CodeGenerationService.js'
import { KinoticProjectConfig } from '@kinotic-ai/core'

export class EntityDefinitionGenerator {
    private readonly codeGenerationService: CodeGenerationService
    private readonly logger: ConsoleLogger

    constructor(
        private readonly application: string,
        private readonly entitiesPath: string,
        private readonly generatedPath: string
    ) {
        this.logger = new ConsoleLogger()
        this.codeGenerationService = new CodeGenerationService(application, '.js', this.logger)
    }

    async generateDefinitions(): Promise<Map<string, ObjectC3Type>> {
        const definitions = new Map<string, ObjectC3Type>()

        const projectConfig = new KinoticProjectConfig()
        projectConfig.application = this.application
        projectConfig.validate = false
        projectConfig.entitiesPaths = [this.entitiesPath]
        projectConfig.generatedPath = this.generatedPath

        await this.codeGenerationService.generateAllEntities(
            projectConfig,
            true,
            async (entityInfo) => {
                definitions.set(entityInfo.entity.name.toLowerCase(), entityInfo.entity)
                this.logger.log(`Generated entity definition for ${entityInfo.entity.name}`)
            }
        )

        return definitions
    }
}
