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
 * the registered tracer, so the API here is kept at the newest release.
 */

if (process.env.OTEL_EXPORTER_OTLP_ENDPOINT) {
    const sdk = new NodeSDK()
    sdk.start()
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
