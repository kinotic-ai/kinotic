import {PersonEntityService} from '@/services/PersonEntityService.js'
import {KinoticOperationTaskGenerator} from '@/tasks/KinoticOperationTaskGenerator.ts'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
import {ConnectionInfo, KinoticSingleton, Pageable} from '@kinotic-ai/core'
import {EntitiesService, PersistencePlugin} from '@kinotic-ai/persistence'
import {OsApiPlugin} from '@kinotic-ai/os-api'
import { ITask } from './ITask';
import opentelemetry, {SpanKind, SpanStatusCode, Tracer} from '@opentelemetry/api'
import info from '../../package.json' assert {type: 'json'}

/**
 * This class will generate tasks to find fake people
 * TODO: update to use admin services
 */
export class MultiTenantFindTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: KinoticOperationTaskGenerator
    private personEntityService: PersonEntityService
    private tracer: Tracer

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                totalToExecute: number,
                pageSize: number,
                totalTenants: number) {

        const kinotic = new KinoticSingleton()
        kinotic.use(OsApiPlugin).use(PersistencePlugin)
        this.personEntityService = new PersonEntityService(new EntitiesService(kinotic))

        this.continuumTaskGenerator = new KinoticOperationTaskGenerator(connectionInfoSupplier,
                                                                        kinotic,
                                                                        totalToExecute,
                                                                        this.createTaskFactory(pageSize))
                                                                          
        console.log('totalTenants', totalTenants)
        // const ids = generateMultipleDeterministicIds(totalTenants)
        this.tracer = opentelemetry.trace.getTracer(
            'structures.load-generator',
            info.version
        )
    }

    getNextTask(): ITask {
        return this.continuumTaskGenerator.getNextTask()
    }

    hasMoreTasks(): boolean {
        return this.continuumTaskGenerator.hasMoreTasks()
    }

    private createTaskFactory(pageSize: number): ITaskFactory {
        return {
            createTask: () => {
                return {
                    name   : () => 'Find All People',
                    execute: async () => {
                        return this.tracer.startActiveSpan(
                            `MultiTenantFindTaskGenerator/findAll`,
                            {
                                kind: SpanKind.CLIENT
                            },
                            async(span) => {
                                return this.personEntityService.findAll(Pageable.create(0, pageSize))
                                           .then(
                                               async (value) => {
                                                   span.end()
                                                   return value
                                               },
                                               async (ex) => {
                                                   span.recordException(ex)
                                                   span.setStatus({ code: SpanStatusCode.ERROR })
                                                   span.end()
                                                   throw ex
                                               })
                                           .then(() => {
                                           })
                            })
                    }
                }
            }
        }
    }

}
