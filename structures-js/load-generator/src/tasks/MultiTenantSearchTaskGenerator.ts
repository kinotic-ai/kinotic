import {PersonEntityService} from '@/services/PersonEntityService.js'
import {ContinuumOperationTaskGenerator} from '@/tasks/ContinuumOperationTaskGenerator.js'
import {ITaskFactory} from '@/tasks/ITaskFactory.js'
import {ITaskGenerator} from '@/tasks/ITaskGenerator.js'
// import {generateMultipleDeterministicIds} from '@/utils/DataUtil.js'
import {ConnectionInfo, ContinuumSingleton, Pageable} from '@kinotic/continuum-client'
import {EntitiesService} from '@kinotic/structures-api'
import { ITask } from './ITask';
import opentelemetry, {SpanKind, SpanStatusCode, Tracer} from '@opentelemetry/api'
import info from '../../package.json' assert {type: 'json'}

/**
 * This class will generate tasks to find fake people
 * TODO: update to use admin services
 */
export class MultiTenantSearchTaskGenerator implements ITaskGenerator {

    private continuumTaskGenerator: ContinuumOperationTaskGenerator
    private personEntityService: PersonEntityService
    private tracer: Tracer

    constructor(connectionInfoSupplier: () => Promise<ConnectionInfo>,
                totalToExecute: number,
                searchText: string,
                pageSize: number,
                totalTenants: number) {

        const continuum = new ContinuumSingleton()
        this.personEntityService = new PersonEntityService(new EntitiesService(continuum.serviceRegistry))

        this.continuumTaskGenerator = new ContinuumOperationTaskGenerator(connectionInfoSupplier,
                                                                          continuum,
                                                                          totalToExecute,
                                                                          this.createTaskFactory(searchText,
                                                                                                 pageSize))
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

    private createTaskFactory(searchText: string, pageSize: number): ITaskFactory {
        return {
            createTask: () => {
                return {
                    name   : () => 'Search People',
                    execute: async () => {
                        return this.tracer.startActiveSpan(
                            `MultiTenantSearchTaskGenerator/search`,
                            {
                                kind: SpanKind.CLIENT
                            },
                            async(span) => {

                                return this.personEntityService.search(searchText,
                                                                       Pageable.create(0, pageSize))
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
