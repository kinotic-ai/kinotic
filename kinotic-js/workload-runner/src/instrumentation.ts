import { readFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { NodeSDK } from '@opentelemetry/sdk-node'

/**
 * Preloaded into the microservice process by supervise.ts, ahead of the project's entry:
 * starts the OpenTelemetry Node SDK when the node issued the workload an OTLP endpoint, so
 * the spans the Kinotic runtime records through the OpenTelemetry API are exported rather
 * than dropped. Everything about the export — endpoint, protocol, bearer token, which
 * signals ship, the service name — comes from the standard OTEL_* variables the node lays
 * into the guest environment; a process without an endpoint runs uninstrumented.
 *
 * The SDK registers against the global OpenTelemetry API, which is how the project's own
 * copy of @opentelemetry/api finds it: a same-major copy no newer than this one resolves
 * the registered tracer, so the API here is kept at the newest release. A project whose copy
 * is newer records nothing, which is reported on stderr as the process starts.
 */

/** The version of the @opentelemetry/api a module in the given directory resolves, or null when it resolves none. */
function apiVersionFrom(directory: string): string | null {
    let ret: string | null = null
    try {
        ret = JSON.parse(readFileSync(Bun.resolveSync('@opentelemetry/api/package.json', directory), 'utf-8')).version
    } catch {
        // A project that resolves no copy from its entry records no spans to lose
    }
    return ret
}

if (process.env.OTEL_EXPORTER_OTLP_ENDPOINT) {
    const sdk = new NodeSDK()
    sdk.start()

    const registered = apiVersionFrom(import.meta.dir)
    const projects = apiVersionFrom(dirname(Bun.main))
    if (registered !== null && projects !== null && Bun.semver.order(projects, registered) > 0) {
        console.error(`[workload-runner] no traces or metrics will be exported: the project resolves `
                      + `@opentelemetry/api ${projects}, newer than the ${registered} this image registers its `
                      + `SDK with. Pin the project's @opentelemetry/api to ${registered} or older until the `
                      + `image is updated`)
    }

    // Flush the batched spans before the process ends. Registering a SIGTERM listener
    // suppresses the default termination, so once the flush is done the signal is re-raised
    // unless the project installed a handler of its own, which then owns the exit.
    process.once('SIGTERM', () => {
        sdk.shutdown()
           .catch(error => console.error('[workload-runner] telemetry flush failed', error))
           .finally(() => {
               if (process.listenerCount('SIGTERM') === 0) {
                   process.kill(process.pid, 'SIGTERM')
               }
           })
    })
}
