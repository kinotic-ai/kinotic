import {ITask} from '@/tasks/ITask.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ITaskGeneratorFactory} from '@/tasks/ITaskGeneratorFactory.js'
import {generateDeterministicId} from '@/utils/DataUtil.js'

export type TenantId = string

/**
 * A task generator that delegates to multiple task generators based on a tenant id
 * NOTE: this class expects that all {@link ITaskGenerator} instances returned by the factory return at least one task
 */
export class MultiTenantTaskGeneratorDelegator implements ITaskGenerator {

    private remainingTenants: number
    private currentTenantId: number
    private currentTaskGenerator: ITaskGenerator
    private readonly taskGeneratorFactory: ITaskGeneratorFactory<TenantId>

    constructor(beginTenantIdNumber: number,
                numberOfTenants: number,
                taskGeneratorFactory: ITaskGeneratorFactory<TenantId>) {
        this.taskGeneratorFactory = taskGeneratorFactory

        this.remainingTenants = numberOfTenants - 1
        this.currentTenantId = beginTenantIdNumber
        this.currentTaskGenerator = taskGeneratorFactory(generateDeterministicId(beginTenantIdNumber))
    }

    getNextTask(): ITask {
        return this.currentTaskGenerator.getNextTask()
    }

    hasMoreTasks(): boolean {
        let hasMoreTasks = this.currentTaskGenerator.hasMoreTasks()
        if(!hasMoreTasks && this.remainingTenants > 0){
            this.remainingTenants--
            this.currentTenantId++
            this.currentTaskGenerator = this.taskGeneratorFactory(generateDeterministicId(this.currentTenantId))
            hasMoreTasks = this.currentTaskGenerator.hasMoreTasks()
        }
        return hasMoreTasks
    }



}
