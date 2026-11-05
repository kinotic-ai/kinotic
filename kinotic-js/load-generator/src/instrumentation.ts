/*instrumentation.ts*/
import {LoadTestConfig} from '@/config/LoadTestConfig.js'
import {OTLPTraceExporter} from '@opentelemetry/exporter-trace-otlp-grpc'
import { NodeSDK } from '@opentelemetry/sdk-node';
import {ConsoleSpanExporter, SpanExporter} from '@opentelemetry/sdk-trace-node'
import { resourceFromAttributes } from '@opentelemetry/resources';
import {
    ATTR_SERVICE_NAME,
    ATTR_SERVICE_VERSION,
} from '@opentelemetry/semantic-conventions';
import info from '../package.json' assert {type: 'json'}
import {OtelConfig, OtelExporterType} from '@/config/OtelConfig.js'

/** What the load test flushes on exit: the started SDK, or nothing when no exporter was configured. */
export interface Instrumentation {
    shutdown(): Promise<void>
}

const otelConfig = OtelConfig.fromEnv()
console.log('Otel Config:')
otelConfig.print()
// We use this with the name to help identify spans
const loadTestConfig = LoadTestConfig.fromEnv()

function startInstrumentation(): Instrumentation {
    let ret: Instrumentation
    if (otelConfig.exporterType === OtelExporterType.NONE) {
        // The SDK is never constructed: started without an exporter it configures its default OTLP one
        ret = { shutdown: () => Promise.resolve() }
    } else {
        let spanExporter: SpanExporter
        if (otelConfig.exporterType === OtelExporterType.OTLP) {
            spanExporter = new OTLPTraceExporter({ url: otelConfig.otelEndpoint })
        } else {
            spanExporter = new ConsoleSpanExporter()
        }
        const sdk = new NodeSDK({
            resource: resourceFromAttributes({
                [ATTR_SERVICE_NAME]: `kinotic.load-generator-${loadTestConfig.testName}`,
                [ATTR_SERVICE_VERSION]: info.version,
            }),
            traceExporter: spanExporter,
        })
        sdk.start()
        ret = sdk
    }
    return ret
}

export const nodeSdk: Instrumentation = startInstrumentation()
