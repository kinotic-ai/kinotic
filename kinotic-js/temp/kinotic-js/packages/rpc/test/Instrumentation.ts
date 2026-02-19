// WARNING: Only import this into tests

/**
 * This is a non-standard setup, because for some reason the console output doesn't work with Otel NodeSDK and vitest.
 * I have stepped through the Otel code, and it seems the console used by Otel NodeSDK is different from the test output.
 */

import {ConsoleSpanExporter, SimpleSpanProcessor} from '@opentelemetry/sdk-trace-node'
import {
    ATTR_SERVICE_NAME
} from '@opentelemetry/semantic-conventions'
import { Resource } from '@opentelemetry/resources'

import { NodeTracerProvider } from '@opentelemetry/sdk-trace-node'
export const otelTracerProvider = new NodeTracerProvider({
                                            resource: new Resource({
                                                                       [ATTR_SERVICE_NAME]: 'ContinuumTests'
                                                                   }),
                                        })

otelTracerProvider.addSpanProcessor(new SimpleSpanProcessor(new ConsoleSpanExporter()))
otelTracerProvider.register()
