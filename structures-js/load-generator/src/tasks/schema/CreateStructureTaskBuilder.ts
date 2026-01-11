import { ITask } from "../ITask"
import { IStructureService, IEntityService, Structures, Structure } from '@mindignited/structures-api'
import { ObjectC3Type } from '@mindignited/continuum-idl'

export interface CreateStructureTaskConfig {
    applicationId: string
    projectId: string
    name: string    
    description: string
    entityDefinitionSupplier: () => ObjectC3Type
    onServiceCreated?: (service: IEntityService<any>) => void
}

export class CreateStructureTaskBuilder {
    private readonly structureService: IStructureService

    constructor(structureService: IStructureService) {
        this.structureService = structureService
    }

    buildTask(config: CreateStructureTaskConfig): ITask {
    
    return {
            name: () => `Create ${config.name} Structure`,
            execute: async () => {
                const structureId = `${config.applicationId}.${config.name.toLowerCase()}`
                const existingStructure = await this.structureService.findById(structureId)
                
                if (existingStructure) {
                    if (existingStructure.published) {
                        await this.structureService.unPublish(existingStructure.id!)
                    }
                    await this.structureService.deleteById(existingStructure.id!)
                    await this.structureService.syncIndex()
                }

                const entityDefinition = config.entityDefinitionSupplier()
                
                const structure = new Structure(
                    config.applicationId,
                    config.projectId,
                    config.name,
                    entityDefinition,
                    config.description
                )
                const savedStructure = await this.structureService.create(structure)
                if (savedStructure.id) {
                    await this.structureService.publish(savedStructure.id)
                }
                
                if (config.onServiceCreated) {
                    const service = Structures.createEntityService(config.applicationId, config.name)
                    config.onServiceCreated(service)
                }
            }
        }
    }
}

export function createStructureTaskBuilder(): CreateStructureTaskBuilder {
    return new CreateStructureTaskBuilder(Structures.getStructureService())
} 