import {PersonRepository} from '@/repository/PersonRepository.js'
import {KinoticOperationTaskGenerator} from '@/tasks/KinoticOperationTaskGenerator.ts'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ConnectionInfo, KinoticSingleton, Pageable} from '@kinotic-ai/core'
import {EntitiesRepository} from '@kinotic-ai/persistence'
import {OsApiPlugin} from '@kinotic-ai/os-api'
import {PersistencePlugin} from '@kinotic-ai/persistence'
import { ITask } from './ITask';

/**
 * This class will generate tasks to find fake people
 */
export class SearchPeopleTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: KinoticOperationTaskGenerator
    private personRepository: PersonRepository


    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                totalToExecute: number,
                searchText: string,
                pageSize: number) {

        const kinotic = new KinoticSingleton()
        kinotic.use(OsApiPlugin).use(PersistencePlugin)
        this.personRepository = new PersonRepository(new EntitiesRepository(kinotic))

        this.continuumTaskGenerator = new KinoticOperationTaskGenerator(connectionInfoSupplier,
                                                                        kinotic,
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
                        return this.personRepository.search(searchText,
                                                               Pageable.create(0, pageSize))
                                                       .then(() =>{})
                    }
                }
            }
        }
    }

}
