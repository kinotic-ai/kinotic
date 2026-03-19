import {ITask} from '@/tasks/ITask.js'

export interface ITaskFactory {

    /**
     * Returns a new task
     */
    createTask(): ITask
}
