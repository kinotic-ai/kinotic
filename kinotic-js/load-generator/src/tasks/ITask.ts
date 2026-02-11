export interface ITask {

    /**
     * The name of the task
     */
    name(): string

    /**
     * Executes the task
     */
    execute(): Promise<void>
}


