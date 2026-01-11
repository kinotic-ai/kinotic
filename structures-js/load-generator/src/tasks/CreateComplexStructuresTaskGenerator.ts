import { ITaskGenerator } from "./ITaskGenerator"
import { ITask } from "./ITask"
import { ConnectionInfo, Continuum } from '@mindignited/continuum-client'
import { EcommerceTaskFactory } from './schema/EcommerceTaskFactory'
import { HealthTaskFactory } from './schema/HealthTaskFactory'

export class CreateComplexStructuresTaskGenerator implements ITaskGenerator {
    private tasks: ITask[] = []
    private currentTaskIndex: number = 0
    private readonly connectionInfoSupplier: () => Promise<ConnectionInfo>
    private readonly ecommerceFactory: EcommerceTaskFactory
    private readonly healthFactory: HealthTaskFactory

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>) {
        this.connectionInfoSupplier = connectionInfoSupplier
        this.ecommerceFactory = new EcommerceTaskFactory()
        this.healthFactory = new HealthTaskFactory()
        this.initialize()
    }

    initialize(): void {
        // Initialize tasks with connection/disconnection at the start/end
        this.tasks = [
            // Connect to Continuum
            {
                name: () => 'Connect to Continuum',
                execute: async () => {
                    const connectionInfo = await this.connectionInfoSupplier()
                    await Continuum.connect(connectionInfo)
                }
            },
            // Ecommerce domain tasks
            ...this.ecommerceFactory.getTasks(),
            // Health domain tasks
            ...this.healthFactory.getTasks(),
            // Disconnect from Continuum
            {
                name: () => 'Disconnect from Continuum',
                execute: async () => {
                    await Continuum.disconnect()
                }
            }
        ]
    }

    getNextTask(): ITask {
        if (this.currentTaskIndex >= this.tasks.length) {
            throw new Error('No more tasks available')
        }
        return this.tasks[this.currentTaskIndex++]
    }

    hasMoreTasks(): boolean {
        return this.currentTaskIndex < this.tasks.length
    }
} 