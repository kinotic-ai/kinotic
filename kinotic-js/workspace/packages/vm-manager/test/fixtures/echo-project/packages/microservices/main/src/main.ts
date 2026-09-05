import { Kinotic, Publish } from '@kinotic-ai/core'
import { ensureNodeWebSocket } from '@kinotic-ai/core/node'
import { appZone } from '@kinotic-ai/management-api'

/**
 * Stands in for a customer project's microservice entry point. The runtime workload runs this
 * file the way it runs any project's — `bun packages/microservices/main/src/main.ts` from the
 * checkout mounted at /app — so what it exercises is the real deployment path, not a harness.
 */
@Publish('e2e.lab')
class EchoService {
    echo(value: string): string {
        return `echo:${value}`
    }
}

ensureNodeWebSocket()
// A project's services live in its application's zone, and the application id comes from the
// project's own configuration rather than from the identity it connects with: the workload
// runs as an organization user, and naming an application in the credentials would scope the
// connection to that application instead.
Kinotic.zonePrefix = appZone(process.env.KINOTIC_ORGANIZATION_ID!,
                             process.env.KINOTIC_PROJECT_APPLICATION_ID!)
await Kinotic.connect()
new EchoService()
console.log(`[echo-project] EchoService published in ${Kinotic.zonePrefix}`)

// The runtime workload is long-lived: supervise.ts restarts the process if it exits
await new Promise(() => {})
