import { ITask } from "../ITask"
import { IEntityDefinitionService, EntityDefinition } from '@kinotic-ai/os-api'
import { IEntityRepository, EntityRepository } from '@kinotic-ai/persistence'
import { ObjectC3Type } from '@kinotic-ai/idl'
import { Kinotic } from '@kinotic-ai/core'

export interface CreateStructureTaskConfig {
    applicationId: string
    projectId: string
    name: string
    description: string
    entityDefinitionSupplier: () => ObjectC3Type
    onServiceCreated?: (service: IEntityRepository<any>) => void
}

export class CreateStructureTaskBuilder {
    private readonly entityDefinitionService: IEntityDefinitionService

    constructor(entityDefinitionService: IEntityDefinitionService) {
        this.entityDefinitionService = entityDefinitionService
    }

    buildTask(config: CreateStructureTaskConfig): ITask {

    return {
            name: () => `Create ${config.name} Structure`,
            execute: async () => {
                const structureId = `${config.applicationId}.${config.name.toLowerCase()}`
                const existingStructure = await this.entityDefinitionService.findById(structureId)

                if (existingStructure) {
                    if (existingStructure.published) {
                        await this.entityDefinitionService.unPublish(existingStructure.id!)
                    }
                    await this.entityDefinitionService.deleteById(existingStructure.id!)
                    await this.entityDefinitionService.syncIndex()
                }

                const schema = config.entityDefinitionSupplier()

                const entityDefinition = new EntityDefinition(
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
                    const service = new EntityRepository(config.applicationId, config.name)
                    config.onServiceCreated(service)
                }
            }
        }
    }
}

export function createStructureTaskBuilder(): CreateStructureTaskBuilder {
    return new CreateStructureTaskBuilder(Kinotic.entityDefinitions)
}
