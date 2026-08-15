package org.kinotic.core.internal.api;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.api.support.RpcTestServiceProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MimeTypeUtils;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

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
    private static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

    @Autowired
    private EventBusService eventBusService;
    @Autowired
    private InMemorySpanExporter spanExporter;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because continuum wires them..
    @Autowired
    private RpcTestServiceProxy rpcTestServiceProxy;

    @BeforeEach
    public void resetSpans(){
        spanExporter.reset();
    }

    @Test
    public void invocationProducesServerSpan(){
        StepVerifier.create(rpcTestServiceProxy.getMonoWithValue())
                    .expectNextCount(1)
                    .expectComplete()
                    .verify();

        SpanData span = awaitSpanFor("getMonoWithValue");

        Assertions.assertEquals(SpanKind.SERVER, span.getKind());
        Assertions.assertEquals("kinotic", span.getAttributes().get(RPC_SYSTEM));
        Assertions.assertEquals("getMonoWithValue", span.getAttributes().get(RPC_METHOD));
        Assertions.assertEquals(span.getAttributes().get(RPC_SERVICE) + "/getMonoWithValue", span.getName());
    }

    @Test
    public void streamingInvocationSpanEndsWithTheStream(){
        StepVerifier.create(rpcTestServiceProxy.getLimitedFlux())
                    .expectNext(1, 2, 3, 4, 5)
                    .expectComplete()
                    .verify();

        SpanData span = awaitSpanFor("getLimitedFlux");

        Assertions.assertEquals(SpanKind.SERVER, span.getKind());
        Assertions.assertTrue(span.getEndEpochNanos() > span.getStartEpochNanos(),
                              "A streaming span must stay open until the stream terminates");
    }

    /**
     * The Kinotic client injects W3C trace context on every send, so an invocation that arrives with a
     * traceparent must continue that trace rather than start a new one.
     */
    @Test
    public void callerTraceContextBecomesTheParent(){
        String traceId = "0af7651916cd43dd8448eb211c80319c";
        String callerSpanId = "b7ad6b7169203331";

        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.CONTENT_TYPE_HEADER, MimeTypeUtils.APPLICATION_JSON_VALUE);
        metadata.put(EventConstants.REPLY_TO_HEADER,
                     EventConstants.REPLY_DESTINATION_SCHEME + "://" + UUID.randomUUID() + "/tracing-test");
        metadata.put(EventConstants.CORRELATION_ID_HEADER, UUID.randomUUID().toString());
        metadata.put(EventConstants.TRACEPARENT_HEADER, "00-" + traceId + "-" + callerSpanId + "-01");

        eventBusService.send(Event.create(CRI.create(serviceCri() + "/getMonoWithValue"),
                                          metadata,
                                          "[]".getBytes(StandardCharsets.UTF_8)));

        SpanData span = awaitSpanFor("getMonoWithValue");

        Assertions.assertEquals(traceId, span.getTraceId());
        Assertions.assertEquals(callerSpanId, span.getParentSpanId());
    }

    /**
     * Asks the platform where the test service is published rather than rebuilding the naming rules,
     * which depend on the zone the test profile runs in.
     */
    private String serviceCri(){
        StepVerifier.create(rpcTestServiceProxy.getMonoWithValue())
                    .expectNextCount(1)
                    .expectComplete()
                    .verify();
        String serviceName = awaitSpanFor("getMonoWithValue").getAttributes().get(RPC_SERVICE);
        spanExporter.reset();
        return EventConstants.SERVICE_DESTINATION_SCHEME + "://" + serviceName;
    }

    private SpanData awaitSpanFor(String methodName){
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .until(() -> !spansFor(methodName).isEmpty());
        return spansFor(methodName).getFirst();
    }

    private List<SpanData> spansFor(String methodName){
        return spanExporter.getFinishedSpanItems()
                           .stream()
                           .filter(span -> methodName.equals(span.getAttributes().get(RPC_METHOD)))
                           .toList();
    }

}
