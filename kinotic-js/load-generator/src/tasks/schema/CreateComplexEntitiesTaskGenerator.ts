import { ITaskGenerator } from "../ITaskGenerator"
import { ITask } from "../ITask"
import { ConnectionInfo, Kinotic } from '@kinotic-ai/core'
import { EcommerceTaskFactory } from './EcommerceTaskFactory'
import { HealthTaskFactory } from './HealthTaskFactory'

export class CreateComplexEntitiesTaskGenerator implements ITaskGenerator {
    private tasks: ITask[] = []
    private currentTaskIndex: number = 0
    private readonly connectionInfoSupplier: () => Promise<ConnectionInfo>
    private readonly ecommerceFactory: EcommerceTaskFactory
    private readonly healthFactory: HealthTaskFactory

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>) {
        this.connectionInfoSupplier = connectionInfoSupplier
        this.ecommerceFactory = new EcommerceTaskFactory(connectionInfoSupplier)
        this.healthFactory = new HealthTaskFactory(connectionInfoSupplier)
        this.initialize()
    }

    initialize(): void {
        // Initialize tasks with connection/disconnection at the start/end
        this.tasks = [
            // Connect to Kinotic
            {
                name: () => 'Connect to Kinotic',
                execute: async () => {
                    const connectionInfo = await this.connectionInfoSupplier()
                    await Kinotic.connect(connectionInfo)
                }
            },
            // Ecommerce domain tasks
            ...this.ecommerceFactory.getTasks(),
            // Health domain tasks
            ...this.healthFactory.getTasks(),
            // Disconnect from Kinotic
            {
                name: () => 'Disconnect from Kinotic',
                execute: async () => {
                    await Kinotic.disconnect()
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