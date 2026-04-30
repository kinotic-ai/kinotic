import { ObjectC3Type } from '@kinotic-ai/idl'
import { ConsoleLogger } from '@kinotic-ai/kinotic-cli/dist/internal/Logger.js'
import { EntityCodeGenerationService } from '@kinotic-ai/kinotic-cli/dist/internal/EntityCodeGenerationService.js'
import { KinoticProjectConfig } from '@kinotic-ai/os-api'

export class EntityDefinitionGenerator {
    private readonly codeGenerationService: EntityCodeGenerationService
    private readonly logger: ConsoleLogger

    constructor(
        private readonly application: string,
        private readonly entitiesPath: string,
        private readonly repositoryPath: string
    ) {
        this.logger = new ConsoleLogger()
        this.codeGenerationService = new EntityCodeGenerationService(application, '.js', this.logger)
    }

    async generateDefinitions(): Promise<Map<string, ObjectC3Type>> {
        const definitions = new Map<string, ObjectC3Type>()

        const projectConfig = new KinoticProjectConfig()
        projectConfig.organization = 'kinotic-test'
        projectConfig.application = this.application
        projectConfig.validate = false
        projectConfig.entitiesPaths = [{
            path: this.entitiesPath,
            repositoryPath: this.repositoryPath,
            mirrorFolderStructure: false
        }]

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
