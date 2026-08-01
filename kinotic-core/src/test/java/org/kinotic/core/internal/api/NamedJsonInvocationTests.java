package org.kinotic.core.internal.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.api.support.DefaultRenamedParameterService;
import org.kinotic.core.internal.api.support.RenamedParameterService;
import org.kinotic.core.internal.utils.MetaUtil;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Pins the invariant the {@link EventConstants#CONTENT_TYPE_NAMED_JSON} wire format rests on: the parameter
 * name the published contract carries is the name an invocation binds by. The two are produced by different
 * resolutions — the {@link SchemaFactory} against the implementation, the invoker's HandlerMethod against
 * whatever {@code AopUtils.selectInvocableMethod} returns — so only an invocation against a service whose
 * implementation renames a parameter can catch them drifting apart. A service whose names already agree
 * passes either way.
 *
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class NamedJsonInvocationTests {

    private static final String QUALIFIED_NAME = RenamedParameterService.class.getPackageName()
            + "." + RenamedParameterService.class.getSimpleName();

    @Autowired
    private EventBusService eventBusService;
    @Autowired
    private SchemaFactory schemaFactory;

    @Test
    public void publishedParameterNameIsTheNameInvocationBindsBy() throws Exception {
        String publishedName = publishedParameterName();

        Event<byte[]> reply = invokeNamedJson("/echo", "{\"" + publishedName + "\":\"hello\"}");
        String body = reply.data() != null ? new String(reply.data(), StandardCharsets.UTF_8) : "";

        // deliberately asserts the two agree rather than which one wins, so it holds whichever
        // declaration is chosen as the naming source
        Assertions.assertNull(reply.metadata().get(EventConstants.ERROR_HEADER),
                              "the contract publishes '" + publishedName
                                      + "' but an invocation sending that name was rejected: " + body);
        Assertions.assertEquals("\"hello\"", body);
    }

    private String publishedParameterName() {
        NamespaceDefinition namespace = schemaFactory.createForServices(
                List.of(new ServiceDeclaration(RenamedParameterService.class, DefaultRenamedParameterService.class)));

        ServiceDefinition service = namespace.getServices()
                                             .stream()
                                             .findFirst()
                                             .orElseThrow(() -> new AssertionError("RenamedParameterService failed to convert"));

        FunctionDefinition echo = service.getFunctions()
                                         .stream()
                                         .filter(function -> function.getName().equals("echo"))
                                         .findFirst()
                                         .orElseThrow();

        return echo.getParameters().getFirst().getName();
    }

    /**
     * Sends a named-JSON invocation the way a remote caller does and returns the reply event.
     */
    private Event<byte[]> invokeNamedJson(String path, String jsonBody) throws Exception {
        CRI replyCri = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME,
                                  "test:" + UUID.randomUUID(),
                                  "NamedJsonProbe");

        EventConsumer consumer = eventBusService.listen(replyCri);
        CompletableFuture<Event<byte[]>> reply = new CompletableFuture<>();
        consumer.handler(reply::complete);
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        try {
            Metadata metadata = Metadata.create();
            metadata.put(EventConstants.REPLY_TO_HEADER, replyCri.raw());
            metadata.put(EventConstants.CORRELATION_ID_HEADER, UUID.randomUUID().toString());
            metadata.put(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TYPE_NAMED_JSON);

            CRI requestCri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME,
                                        null,
                                        QUALIFIED_NAME,
                                        path,
                                        MetaUtil.getVersion(RenamedParameterService.class));

            eventBusService.sendWithAck(Event.create(requestCri, metadata, jsonBody.getBytes(StandardCharsets.UTF_8)))
                           .toCompletionStage()
                           .toCompletableFuture()
                           .get(10, TimeUnit.SECONDS);

            return reply.get(15, TimeUnit.SECONDS);
        } finally {
            consumer.unregister();
        }
    }

}
