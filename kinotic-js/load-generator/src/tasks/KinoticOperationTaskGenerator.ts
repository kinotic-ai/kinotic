import {ITask} from './ITask.js'
import {ITaskFactory} from './ITaskFactory.js'
import {ITaskGenerator} from './ITaskGenerator.js'
import {ConnectionInfo, KinoticSingleton} from '@kinotic-ai/core'

class ContinuumTask implements ITask{
    private delegate: ITask
    private continuumGenerator: KinoticOperationTaskGenerator

    constructor(delegate: ITask,
                continuumGenerator: KinoticOperationTaskGenerator) {
        this.delegate = delegate
        this.continuumGenerator = continuumGenerator
    }

    name(): string {
        return this.delegate.name()
    }

    async execute(): Promise<void> {
        await this.continuumGenerator.awaitConnectionComplete()
        const ret = await this.delegate.execute()
        this.continuumGenerator.markTaskComplete()
        return ret
    }
}

/**
 * A {@link TaskGenerator} that will generate tasks that will execute on a {@link KinoticSingleton}
 */
export class KinoticOperationTaskGenerator implements ITaskGenerator{

    private readonly connectionInfoSupplier: () => Promise<ConnectionInfo>
    private taskFactory: ITaskFactory
    private taskCreationsRemaining: number
    private readonly totalTasks: number = 0
    private taskCompletionCount: number = 0
    private readonly tasksComplete: Promise<void> | null = null
    private resolveAllTasksComplete: ((value: void) => void) | null = null
    private readonly kinoticConnected: Promise<void>
    private resolveKinoticConnected: ((value: void) => void) | null = null
    private readonly kinotic: KinoticSingleton

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                kinotic: KinoticSingleton,
                totalTasks: number,
                taskFactory: ITaskFactory) {
        this.connectionInfoSupplier = connectionInfoSupplier
        this.kinotic = kinotic
        this.taskFactory = taskFactory
        this.totalTasks = totalTasks + 2// we add 2 for the connect and disconnect tasks
        this.taskCreationsRemaining = this.totalTasks
        this.tasksComplete = new Promise<void>((resolve) => {
            this.resolveAllTasksComplete = resolve
        })
        this.kinoticConnected = new Promise<void>((resolve) => {
            this.resolveKinoticConnected = resolve
        })
    }

    getNextTask(): ITask {
        if(this.taskCreationsRemaining === this.totalTasks){
            this.taskCreationsRemaining--
            return {
                name: () => 'Connect Kinotic',
                execute: async () => {
                    const connectionInfo = await this.connectionInfoSupplier()
                    await this.kinotic.connect(connectionInfo)
                    this.resolveKinoticConnected!()
                }
            }
        }else if(this.taskCreationsRemaining === 1){
            this.taskCreationsRemaining--
            return {
                name: () => 'Disconnect Kinotic',
                execute: async () => {
                    await this.tasksComplete // Wait for all tasks to complete before disconnecting
                    await this.kinotic.disconnect()
                }
            }
        }else{
            this.taskCreationsRemaining--
            return new ContinuumTask(this.taskFactory.createTask(), this)
        }
    }

    hasMoreTasks(): boolean {
        return this.taskCreationsRemaining > 0
    }

    awaitConnectionComplete(): Promise<void> {
        return this.kinoticConnected!
    }

    markTaskComplete(): void {
        this.taskCompletionCount++
        if(this.taskCompletionCount === (this.totalTasks-2)){ // We subtract 2 for the connect and disconnect tasks
            if(this.resolveAllTasksComplete){
                this.resolveAllTasksComplete()
                this.resolveAllTasksComplete = null
            }
        }
    }

}
