import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import PQueue from 'p-queue'

export class TaskExecutionService {

    private taskGenerator: ITaskGenerator
    private maxQueueDepth: number
    private queue: PQueue
    private started: boolean = false
    private completedPromise: Promise<void> | null = null
    private completeResolver: ((value: void) => void) | null = null


    constructor(concurrency: number,
                maxExecutionsPerSecond: number,
                maxQueueDepth: number,
                taskGenerator: ITaskGenerator) {

        this.queue = new PQueue({
                                    concurrency: concurrency,
                                    interval: 1000,
                                    intervalCap: maxExecutionsPerSecond,
                                    carryoverConcurrencyCount: true,
                                    autoStart: false
                                })
        this.maxQueueDepth = maxQueueDepth
        this.taskGenerator = taskGenerator

        this.queue.on('error', error => {
            console.error(error, "Error executing task")
        })

        this.queue.on('empty', async () => {
            let tasksAdded = false
            if(this.taskGenerator.hasMoreTasks()){
                tasksAdded = this.enqueueTasks()
            }
            if(!tasksAdded) {
                await this.queue.onIdle() // make sure all inflight tasks are completed
                await this.stop().catch(err => console.error(err, "Error stopping Task Execution Service"))
            }
        });
    }

    public async start(): Promise<void>{
        if(!this.started) {
            this.started = true
            console.log('Starting Task Execution Service')
            this.completedPromise = new Promise<void>((resolve) => {
                this.completeResolver = resolve
            })
            this.enqueueTasks()
            this.queue.start()
        }else{
            throw new Error('Task Execution Service already started')
        }
    }

    public async stop(): Promise<void> {
        if (this.started) {
            this.started = false
            console.log('Stopping Task Execution Service')
            this.queue.pause()
            this.queue.clear()
            if (this.completeResolver) {
                this.completeResolver()
                this.completeResolver = null
            }
        }
    }

    public async waitForCompletion(): Promise<void> {
        if(this.completedPromise){
            return this.completedPromise
        }else{
            throw new Error('Task Execution Service not started')
        }
    }

    private enqueueTasks(): boolean {
        let ret = false
        let numberOfTasks = this.maxQueueDepth - this.queue.size
        for(let i = 0; i < numberOfTasks; i++) {
            if(this.taskGenerator.hasMoreTasks()) {
                const task = this.taskGenerator.getNextTask()
                this.queue.add(() => {
                    console.log(`Executing task ${task.name()}`)
                    return task.execute()
                }).catch((e) => {
                    console.error(e, `Error executing task ${task.name}`);
                })
                ret = true
            }else {
                break
            }
        }
        return ret
    }

}
