import { ITask } from "../ITask"
import { IEntityDefinitionService, EntityDefinition } from '@kinotic-ai/os-api'
import { IEntityRepository, EntityRepository, EntitiesRepository } from '@kinotic-ai/persistence'
import { ObjectC3Type } from '@kinotic-ai/idl'
import { Kinotic, KinoticSingleton } from '@kinotic-ai/core'

export interface CreateKinoticTaskConfig {
    organizationId: string
    applicationId: string
    projectId: string
    name: string
    description: string
    entityDefinitionSupplier: () => ObjectC3Type
    /**
     * Returns the APPLICATION-scoped Kinotic that backs the EntityRepository
     * passed to onServiceCreated. Called lazily so the caller can connect the
     * client after the application has been created.
     */
    appKinoticSupplier?: () => KinoticSingleton
    onServiceCreated?: (service: IEntityRepository<any>) => void
}

export class CreateKinoticTaskBuilder {
    private readonly entityDefinitionService: IEntityDefinitionService

    constructor(entityDefinitionService: IEntityDefinitionService) {
        this.entityDefinitionService = entityDefinitionService
    }

    buildTask(config: CreateKinoticTaskConfig): ITask {

    return {
            name: () => `Create ${config.name} EntityDefinition`,
            execute: async () => {
                const entityDefinitionId = `${config.organizationId}.${config.applicationId}.${config.name}`.toLowerCase()
                const existingEntityDefinition = await this.entityDefinitionService.findById(entityDefinitionId)

                if (existingEntityDefinition) {
                    if (existingEntityDefinition.published) {
                        await this.entityDefinitionService.unPublish(existingEntityDefinition.id!)
                    }
                    await this.entityDefinitionService.deleteById(existingEntityDefinition.id!)
                    await this.entityDefinitionService.syncIndex()
                }

                const schema = config.entityDefinitionSupplier()

                const entityDefinition = new EntityDefinition(
                    config.organizationId,
                    config.applicationId,
                    config.projectId,
                    config.name,
                    schema,
                    config.description
                )
                const savedEntityDefinition = await this.entityDefinitionService.create(entityDefinition)
                if (savedEntityDefinition.id) {
                    await this.entityDefinitionService.publish(savedEntityDefinition.id)
                }

                if (config.onServiceCreated) {
                    const appKinotic = config.appKinoticSupplier?.()
                    const entitiesRepository = appKinotic ? new EntitiesRepository(appKinotic) : undefined
                    const service = new EntityRepository(config.organizationId, config.applicationId, config.name, entitiesRepository)
                    config.onServiceCreated(service)
                }
            }
        }
    }
}

export function createKinoticTaskBuilder(): CreateKinoticTaskBuilder {
    return new CreateKinoticTaskBuilder(Kinotic.entityDefinitions)
}
