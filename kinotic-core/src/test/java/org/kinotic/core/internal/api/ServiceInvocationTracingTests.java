package org.kinotic.core.internal.api;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.internal.api.support.RpcTestServiceProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

/**
 * Verifies the spans {@link org.kinotic.core.internal.api.service.invoker.ServiceInvocationSupervisor}
 * produces for a service invocation, through the same event bus path a remote caller uses.
 *
 * Created by Claude on 2026-08-15.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class ServiceInvocationTracingTests {

    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

    @Autowired
    private InMemorySpanExporter spanExporter;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because Kinotic wires them..
    @Autowired
    private RpcTestServiceProxy rpcTestServiceProxy;

    @BeforeEach
    public void resetSpans(){
        spanExporter.reset();
    }

    @Test
    public void invocationProducesServerSpan(){
        StepVerifier.create(rpcTestServiceProxy.getMonoWithValue())
                    .thenConsumeWhile(value -> true)
                    .expectComplete()
                    .verify();

        SpanData span = awaitSpanFor("getMonoWithValue");

        Assertions.assertEquals(SpanKind.SERVER, span.getKind());
        Assertions.assertEquals("kinotic", span.getAttributes().get(RPC_SYSTEM));
        Assertions.assertEquals("RpcTestService/getMonoWithValue", span.getName());
    }

    @Test
    public void streamingInvocationSpanEndsWithTheStream(){
        StepVerifier.create(rpcTestServiceProxy.getLimitedFlux())
                    .thenConsumeWhile(value -> true)
                    .expectComplete()
                    .verify();

        SpanData span = awaitSpanFor("getLimitedFlux");

        Assertions.assertEquals(SpanKind.SERVER, span.getKind());
        Assertions.assertTrue(span.getEndEpochNanos() > span.getStartEpochNanos(),
                              "A streaming span must stay open until the stream terminates");
    }

    /**
     * A proxy invocation is the platform calling itself, so both halves belong to one trace rather
     * than the callee starting its own.
     */
    @Test
    public void proxyInvocationLinksClientAndServerSpans(){
        StepVerifier.create(rpcTestServiceProxy.getMonoWithValue())
                    .thenConsumeWhile(value -> true)
                    .expectComplete()
                    .verify();

        SpanData clientSpan = awaitSpanFor("getMonoWithValue", SpanKind.CLIENT);
        SpanData serverSpan = awaitSpanFor("getMonoWithValue", SpanKind.SERVER);

        Assertions.assertEquals(clientSpan.getTraceId(), serverSpan.getTraceId());
        Assertions.assertEquals(clientSpan.getSpanId(), serverSpan.getParentSpanId());
    }

    private SpanData awaitSpanFor(String methodName){
        return awaitSpanFor(methodName, SpanKind.SERVER);
    }

    /**
     * A proxy invocation produces a client and a server span for the same method, so the kind is what
     * separates the two halves of the call.
     */
    private SpanData awaitSpanFor(String methodName, SpanKind kind){
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .until(() -> !spansFor(methodName, kind).isEmpty());
        return spansFor(methodName, kind).getFirst();
    }

    private List<SpanData> spansFor(String methodName, SpanKind kind){
        return spanExporter.getFinishedSpanItems()
                           .stream()
                           .filter(span -> methodName.equals(span.getAttributes().get(RPC_METHOD))
                                   && span.getKind() == kind)
                           .toList();
    }

}
