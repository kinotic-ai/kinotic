import {ITask} from './ITask.js'

export interface ITaskGenerator {

    /**
     * Returns the next task to be executed
     */
    getNextTask(): ITask

    /**
     * Returns true if there are more tasks to be executed
     */
    hasMoreTasks(): boolean

}
