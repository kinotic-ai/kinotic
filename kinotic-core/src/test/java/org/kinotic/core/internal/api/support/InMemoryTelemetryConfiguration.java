package org.kinotic.core.internal.api.support;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides a real OpenTelemetry SDK that keeps finished spans in memory, so a test can assert on
 * what the platform actually exported. Production resolves {@code GlobalOpenTelemetry}, which is
 * a no-op without the java agent attached.
 *
 * Component scanned rather than imported per test: Ignite allows one default instance per JVM, so
 * every test in this module has to share the one application context.
 *
 * Created by Claude on 2026-08-15.
 */
@Configuration
public class InMemoryTelemetryConfiguration {

    @Bean
    public InMemorySpanExporter inMemorySpanExporter(){
        return InMemorySpanExporter.create();
    }

    /**
     * Marked primary rather than replacing the platform bean by name: KinoticOpenTelemetryConfig is
     * component scanned, so its @ConditionalOnMissingBean is evaluated before this one is registered.
     */
    @Bean
    @Primary
    public OpenTelemetry testOpenTelemetry(InMemorySpanExporter inMemorySpanExporter){
        // Simple rather than batch, so a span is readable as soon as it ends
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                                                           .addSpanProcessor(SimpleSpanProcessor.create(inMemorySpanExporter))
                                                           .build();
        return OpenTelemetrySdk.builder()
                               .setTracerProvider(tracerProvider)
                               .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                               .build();
    }

}
