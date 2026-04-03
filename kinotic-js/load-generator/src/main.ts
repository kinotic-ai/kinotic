import {ConcurrencyConfig} from '@/config/ConcurrencyConfig.js'
import {LoadTestConfig} from '@/config/LoadTestConfig.js'
import {KinoticConnectionConfig} from '@/config/KinoticConnectionConfig.ts'
import {nodeSdk} from '@/instrumentation.js'
import {LoadTaskGeneratorFactory} from '@/repository/LoadTaskGeneratorFactory.js'
import {TaskExecutionService} from '@/repository/TaskExecutionService.js'
import {formatDuration} from '@/utils/DataUtil.js'

import {Kinotic} from '@kinotic-ai/core'
import {OsApiPlugin} from '@kinotic-ai/os-api'
import {PersistencePlugin} from '@kinotic-ai/persistence'
import {WebSocket} from 'ws'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

Kinotic.use(OsApiPlugin)
       .use(PersistencePlugin)

try {

    const concurrencyConfig = ConcurrencyConfig.fromEnv()
    const kinoticConnectionConfig = KinoticConnectionConfig.fromEnv()
    const loadTestConfig = LoadTestConfig.fromEnv()
    console.log('Load Generator Config:')
    concurrencyConfig.print()
    kinoticConnectionConfig.print()
    loadTestConfig.print()

    const taskGenerator = LoadTaskGeneratorFactory.createTaskGenerator(kinoticConnectionConfig, loadTestConfig)
    const taskExecutor = new TaskExecutionService(concurrencyConfig.maxConcurrentRequests,
                                                  concurrencyConfig.maxRequestsPerSecond,
                                                  100,
                                                  taskGenerator)

    process
        .on('unhandledRejection', (reason, p) => {
            console.error(reason, 'Unhandled Rejection at Promise', p)
        })
        .on('uncaughtException', async err => {
            console.error(err, 'Uncaught Exception thrown')
            await taskExecutor.stop()
            await nodeSdk.shutdown().catch(console.error)
            process.exit(1)
        }).on('SIGINT', async () => {
            console.log('SIGINT')
            await taskExecutor.stop()
            await nodeSdk.shutdown().catch(console.error)
        }).on('SIGTERM', async () => {
            console.log('SIGTERM')
            await taskExecutor.stop()
            await nodeSdk.shutdown().catch(console.error)
        })

    try {
        const start = performance.now()
        
        console.log('Waiting 1 minute before starting tasks...')
        await new Promise(resolve => setTimeout(resolve, 60000)) // 1 minute delay
        
        await taskExecutor.start()
        console.log('Load Generator Started')

        await taskExecutor.waitForCompletion()

        console.log('Load Generator Completed')
        const end = performance.now()    // Record end time
        const duration = end - start     // Calculate the duratio
        console.log(`Load Generation took ${formatDuration(duration)}.`)

        await nodeSdk.shutdown().catch(console.error)
    } finally {
        await taskExecutor.stop()
    }

} catch (e: any) {
    console.error(e, 'Exception thrown')
    await nodeSdk.shutdown().catch(console.error)
}

