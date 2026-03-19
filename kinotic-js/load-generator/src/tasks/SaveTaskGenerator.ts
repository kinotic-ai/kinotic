import {PersonEntityService} from '@/services/PersonEntityService.js'
import {KinoticOperationTaskGenerator} from '@/tasks/KinoticOperationTaskGenerator.ts'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {generatePeople} from '@/utils/DataUtil.js'
import {ConnectionInfo, KinoticSingleton} from '@kinotic-ai/core'
import {EntitiesService} from '@kinotic-ai/persistence'
import {OsApiPlugin} from '@kinotic-ai/os-api'
import {PersistencePlugin} from '@kinotic-ai/persistence'
import { ITask } from './ITask';

/**
 * This class will generate tasks to create fake {@link Person} objects
 */
export class SaveTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: KinoticOperationTaskGenerator
    private personEntityService: PersonEntityService

    /**
     * Constructs a {@link SaveTaskGenerator}
     * NOTE: numberOfPeopleToCreate must be evenly divisible by batchSize or an error will be thrown
     * @param connectionInfoSupplier the supplier to get the {@link ConnectionInfo} to use
     * @param batchSize the number of people to create in a single batch.
     *                  If the batch size is 1 save will be used instead of bulk save
     * @param numberOfPeopleToCreate the total number of people to create
     */
    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                batchSize: number,
                numberOfPeopleToCreate: number) {
        if(numberOfPeopleToCreate % batchSize !== 0) {
            throw new Error('numberOfPeopleToCreate must be evenly divisible by batchSize')
        }
        const kinotic = new KinoticSingleton()
        kinotic.use(OsApiPlugin).use(PersistencePlugin)
        this.personEntityService = new PersonEntityService(new EntitiesService(kinotic))

        this.continuumTaskGenerator = new KinoticOperationTaskGenerator(connectionInfoSupplier,
                                                                        kinotic,
                                                                        numberOfPeopleToCreate / batchSize,
                                                                        this.createTaskFactory(batchSize))
    }

    getNextTask(): ITask {
        return this.continuumTaskGenerator.getNextTask()
    }

    hasMoreTasks(): boolean {
        return this.continuumTaskGenerator.hasMoreTasks()
    }

    private createTaskFactory(batchSize: number): ITaskFactory {
        if(batchSize === 1) {
            return {
                createTask: () => {
                    return {
                        name   : () => 'Bulk Save People',
                        execute: async () => {
                            return this.personEntityService.save(generatePeople(batchSize)[0])
                                                           .then(() => {})
                        }
                    }
                }
            }
        }else {
            return {
                createTask: () => {
                    return {
                        name   : () => 'Bulk Save People',
                        execute: async () => {
                            return this.personEntityService.bulkSave(generatePeople(batchSize))
                        }
                    }
                }
            }
        }
    }

}
