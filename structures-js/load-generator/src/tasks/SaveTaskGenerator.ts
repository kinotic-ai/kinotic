import {PersonEntityService} from '@/services/PersonEntityService.js'
import {ContinuumOperationTaskGenerator} from '@/tasks/ContinuumOperationTaskGenerator.js'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {generatePeople} from '@/utils/DataUtil.js'
import {ConnectionInfo, ContinuumSingleton} from '@mindignited/continuum-client'
import {EntitiesService} from '@mindignited/structures-api'
import { ITask } from './ITask';

/**
 * This class will generate tasks to create fake {@link Person} objects
 */
export class SaveTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: ContinuumOperationTaskGenerator
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
        const continuum = new ContinuumSingleton()
        this.personEntityService = new PersonEntityService(new EntitiesService(continuum.serviceRegistry))

        this.continuumTaskGenerator = new ContinuumOperationTaskGenerator(connectionInfoSupplier,
                                                                          continuum,
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
