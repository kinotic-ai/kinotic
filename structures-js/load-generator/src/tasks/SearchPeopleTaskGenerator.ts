import {PersonEntityService} from '@/services/PersonEntityService.js'
import {ContinuumOperationTaskGenerator} from '@/tasks/ContinuumOperationTaskGenerator.js'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ConnectionInfo, ContinuumSingleton, Pageable} from '@kinotic/continuum-client'
import {EntitiesService} from '@kinotic/structures-api'
import { ITask } from './ITask';

/**
 * This class will generate tasks to find fake people
 */
export class SearchPeopleTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: ContinuumOperationTaskGenerator
    private personEntityService: PersonEntityService


    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                totalToExecute: number,
                searchText: string,
                pageSize: number) {

        const continuum = new ContinuumSingleton()
        this.personEntityService = new PersonEntityService(new EntitiesService(continuum.serviceRegistry))

        this.continuumTaskGenerator = new ContinuumOperationTaskGenerator(connectionInfoSupplier,
                                                                          continuum,
                                                                          totalToExecute,
                                                                          this.createTaskFactory(searchText,
                                                                                                 pageSize))
    }

    getNextTask(): ITask {
        return this.continuumTaskGenerator.getNextTask()
    }

    hasMoreTasks(): boolean {
        return this.continuumTaskGenerator.hasMoreTasks()
    }

    private createTaskFactory(searchText: string, pageSize: number): ITaskFactory {
        return {
            createTask: () => {
                return {
                    name   : () => 'Search People',
                    execute: async () => {
                        return this.personEntityService.search(searchText,
                                                               Pageable.create(0, pageSize))
                                                       .then(() =>{})
                    }
                }
            }
        }
    }

}
