import {PersonEntityService} from '@/services/PersonEntityService.js'
import {ContinuumOperationTaskGenerator} from '@/tasks/ContinuumOperationTaskGenerator.js'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ConnectionInfo, ContinuumSingleton, Pageable} from '@mindignited/continuum-client'
import {EntitiesService} from '@mindignited/structures-api'
import { ITask } from './ITask';

/**
 * This class will generate tasks to find fake people
 */
export class FindTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: ContinuumOperationTaskGenerator
    private personEntityService: PersonEntityService

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                totalToExecute: number,
                pageSize: number) {

        const continuum = new ContinuumSingleton()
        this.personEntityService = new PersonEntityService(new EntitiesService(continuum.serviceRegistry))

        this.continuumTaskGenerator = new ContinuumOperationTaskGenerator(connectionInfoSupplier,
                                                                          continuum,
                                                                          totalToExecute,
                                                                          this.createTaskFactory(pageSize))
    }

    getNextTask(): ITask {
        return this.continuumTaskGenerator.getNextTask()
    }

    hasMoreTasks(): boolean {
        return this.continuumTaskGenerator.hasMoreTasks()
    }

    private createTaskFactory(pageSize: number): ITaskFactory {
        return {
            createTask: () => {
                return {
                    name   : () => 'Find All People',
                    execute: async () => {
                        return this.personEntityService.findAll(Pageable.create(0, pageSize))
                                                       .then(() =>{})
                    }
                }
            }
        }
    }

}
