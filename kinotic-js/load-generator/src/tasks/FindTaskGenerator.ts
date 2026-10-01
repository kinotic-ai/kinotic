import {PersonRepository} from '@/repository/PersonRepository.js'
import {KinoticOperationTaskGenerator} from '@/tasks/KinoticOperationTaskGenerator.ts'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ConnectionInfo, KinoticSingleton, Pageable} from '@kinotic-ai/core'
import {EntitiesRepository} from '@kinotic-ai/persistence'
import {ManagementApiPlugin} from '@kinotic-ai/management-api'
import {PersistencePlugin} from '@kinotic-ai/persistence'
import { ITask } from './ITask';

/**
 * This class will generate tasks to find fake people
 */
export class FindTaskGenerator implements ITaskGenerator {

    private kinoticTaskGenerator: KinoticOperationTaskGenerator
    private personRepository: PersonRepository

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                totalToExecute: number,
                pageSize: number) {

        const kinotic = new KinoticSingleton()
        kinotic.use(ManagementApiPlugin).use(PersistencePlugin)
        this.personRepository = new PersonRepository(new EntitiesRepository(kinotic))

        this.kinoticTaskGenerator = new KinoticOperationTaskGenerator(connectionInfoSupplier,
                                                                        kinotic,
                                                                        totalToExecute,
                                                                        this.createTaskFactory(pageSize))
    }

    getNextTask(): ITask {
        return this.kinoticTaskGenerator.getNextTask()
    }

    hasMoreTasks(): boolean {
        return this.kinoticTaskGenerator.hasMoreTasks()
    }

    private createTaskFactory(pageSize: number): ITaskFactory {
        return {
            createTask: () => {
                return {
                    name   : () => 'Find All People',
                    execute: async () => {
                        return this.personRepository.findAll(Pageable.create(0, pageSize))
                                                       .then(() =>{})
                    }
                }
            }
        }
    }

}
