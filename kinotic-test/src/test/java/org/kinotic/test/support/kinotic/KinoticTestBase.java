package org.kinotic.test.support.kinotic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.junit.jupiter.api.function.Executable;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test base for tests that need the Kinotic stack (Elasticsearch + kinotic-migration + OpenFGA)
 * via Docker Compose (compose.kinotic-test.yml).
 * Uses Testcontainers Docker Compose support.
 */
@ContextConfiguration(initializers = KinoticTestContextInitializer.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class KinoticTestBase {

    /**
     * Default organization id used by test fixtures. Scoped CRUD services derive
     * Elasticsearch routing keys and ID prefixes from this value, so all test entities
     * are co-located under one org.
     */
    public static final String TEST_ORG_ID = "kinotic";

    /**
     * Default application id used by test fixtures. Matches the Application that the sample
     * {@link TestDataService} creates its EntityDefinitions under.
     */
    public static final String TEST_APP_ID = TestDataService.SAMPLE_APP_ID;

    /**
     * The {@link OrganizationParticipant} that {@link #runAsOrganization(Supplier)} binds to
     * the current Vert.x context. Carries {@link #TEST_ORG_ID} so that org-scoped services
     * accept and filter test fixtures under a single organization.
     */
    public static final OrganizationParticipant TEST_ORGANIZATION_PARTICIPANT =
            new DefaultOrganizationParticipant("test-user",
                                               TEST_ORG_ID,
                                               Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                      ParticipantConstants.PARTICIPANT_TYPE_USER),
                                               List.of("ADMIN"));

    // The binding that makes the test participant the test organization's administrator, as the member who
    // signed up for it would be; the store outlives the test contexts, so it is written once per run
    private static final String TEST_PARTICIPANT_ADMIN_BINDING = "test-user-administers-" + TEST_ORG_ID;
    private static boolean testParticipantAdministers;

    @Autowired
    protected Vertx vertx;

    @Autowired
    protected SecurityContext securityContext;

    @Autowired
    private RelationshipService relationshipService;

    @Autowired
    private RequestAuthorizer requestAuthorizer;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private JsonMapper jsonMapper;

    /**
     * Makes {@link #TEST_ORGANIZATION_PARTICIPANT} the administrator of {@link #TEST_ORG_ID} in the platform
     * store, so the listings that filter to what the caller may see show it everything, as they do an
     * organization's administrator.
     */
    @BeforeEach
    public void bindTestParticipantAsAdministrator() throws Exception {
        if (!testParticipantAdministers) {
            // the store accepts the binding once the reconciler has written the platform model
            assertTrue(awaitUntil(this::bound), "the platform store never accepted the test participant's administrator binding");
            testParticipantAdministers = true;
        }
    }

    private boolean bound() {
        boolean ret;
        try {
            relationshipService.ensureBound(AuthzStoreService.PLATFORM,
                                            TEST_PARTICIPANT_ADMIN_BINDING,
                                            AuthzUtil.ORGANIZATION_ADMIN_ROLE,
                                            AuthzUtil.object(AuthzUtil.USER_TYPE, TEST_ORGANIZATION_PARTICIPANT.getId()),
                                            AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, TEST_ORG_ID))
                               .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            ret = true;
        } catch (Exception e) {
            ret = false;
        }
        return ret;
    }

    /**
     * Builds an {@link ApplicationParticipant} for use as an {@code EntityContext} participant,
     * scoped to {@link #TEST_ORG_ID}/{@link #TEST_APP_ID}. The {@code tenantId} selects which
     * slice of the Application's end-user data the participant's entity operations read and write.
     *
     * @param tenantId the tenant slice the participant belongs to
     * @param id       the participant identity
     */
    public static ApplicationParticipant applicationParticipant(String tenantId, String id) {
        return new DefaultApplicationParticipant(id,
                                                 TEST_ORG_ID,
                                                 TEST_APP_ID,
                                                 tenantId,
                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                        ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                 List.of("USER"));
    }

    /**
     * Builds an {@link ApplicationParticipant} with tenant {@code "kinotic"} and id
     * {@code "dummy"}, for single-tenant tests that don't exercise tenant isolation.
     */
    public static ApplicationParticipant applicationParticipant() {
        return applicationParticipant("kinotic", "dummy");
    }

    /**
     * Runs the supplied async operation on a Vert.x context with
     * {@link #TEST_ORGANIZATION_PARTICIPANT} bound, so that org-scoped services resolve
     * {@link #TEST_ORG_ID} from the current participant. This lets tests exercise scoped
     * services without authenticating through the gateway.
     */
    protected <T> Future<T> runAsOrganization(Supplier<Future<T>> supplier) {
        return runAs(TEST_ORGANIZATION_PARTICIPANT, supplier);
    }

    /**
     * Runs the supplied async operation on a Vert.x context with the given participant bound, as
     * {@link #runAsOrganization(Supplier)} does with {@link #TEST_ORGANIZATION_PARTICIPANT}.
     */
    protected <T> Future<T> runAs(Participant participant, Supplier<Future<T>> supplier) {
        Promise<T> promise = Promise.promise();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            securityContext.setParticipant(context, participant);
            try {
                supplier.get().onComplete(promise);
            } catch (Throwable t) {
                // a service that throws synchronously would otherwise leave the promise uncompleted, hanging the test
                promise.fail(t);
            }
        });
        return promise.future();
    }

    /**
     * Authorizes a request to a service's function as the gateway does when the caller sends it, with the
     * arguments in order as the JSON body.
     *
     * @param service   the service's directory entry id, its zone and qualified name
     * @param function  the function requested
     * @param caller    the participant making the request
     * @param arguments the function's arguments in order
     * @throws ExecutionException caused by an {@link AuthorizationException} when the caller is refused
     */
    protected void authorize(String service, String function, Participant caller, Object arguments) throws Exception {
        requestAuthorizer.authorize(cri(service, function, caller), caller, EventConstants.CONTENT_TYPE_JSON,
                                    jsonMapper.writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8))
                         .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    /**
     * Authorizes a tool call as the MCP endpoint does, with the function's arguments by parameter name.
     *
     * @throws ExecutionException caused by an {@link AuthorizationException} when the caller is refused
     */
    protected void authorizeToolCall(String service, String function, Participant caller, Map<String, ?> arguments) throws Exception {
        requestAuthorizer.authorize(cri(service, function, caller), caller, jsonMapper.<ObjectNode>valueToTree(arguments))
                         .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    /**
     * @return whether {@link #authorize(String, String, Participant, Object)} admits the request; a request
     *         failing for any reason but a refusal fails the test
     */
    protected boolean admitted(String service, String function, Participant caller, Object arguments) throws Exception {
        boolean ret;
        try {
            authorize(service, function, caller, arguments);
            ret = true;
        } catch (ExecutionException refused) {
            assertInstanceOf(AuthorizationException.class, refused.getCause());
            ret = false;
        }
        return ret;
    }

    /**
     * Asserts {@link #authorize(String, String, Participant, Object)} refuses the request with a message
     * containing {@code naming}, the permission or object the refusal names.
     */
    protected void assertRefused(String service, String function, Participant caller, Object arguments, String naming) throws Exception {
        assertRefusal(() -> authorize(service, function, caller, arguments), naming);
    }

    /**
     * Asserts {@link #authorizeToolCall(String, String, Participant, Map)} refuses the call as
     * {@link #assertRefused(String, String, Participant, Object, String)} asserts of a request.
     */
    protected void assertToolCallRefused(String service, String function, Participant caller, Map<String, ?> arguments, String naming) throws Exception {
        assertRefusal(() -> authorizeToolCall(service, function, caller, arguments), naming);
    }

    private static void assertRefusal(Executable authorization, String naming) {
        ExecutionException failure = assertThrows(ExecutionException.class, authorization);
        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains(naming), refused.getMessage());
    }

    /**
     * The destination of a request to a service's function at the version the directory holds for the service,
     * addressed in the caller's scope: an {@link ApplicationParticipant} addresses its application, an
     * {@link OrganizationParticipant} its organization, and a {@link SystemParticipant} the service unscoped.
     *
     * @param service  the service's directory entry id, its zone and qualified name
     * @param function the function requested
     * @param caller   the participant making the request
     */
    protected CRI cri(String service, String function, Participant caller) throws Exception {
        ServiceDirectoryEntry entry = serviceDirectory.findEntry(service).toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertNotNull(entry, "the directory holds no contract for " + service);
        String scope = switch ((ScopedParticipant) caller) {
            case ApplicationParticipant application -> application.getApplicationId();
            case OrganizationParticipant organization -> organization.getOrganizationId();
            case SystemParticipant _ -> null;
        };
        return CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, scope, service, "/" + function, entry.getVersion());
    }

    /**
     * Polls the condition every quarter second for up to thirty seconds.
     *
     * @return whether the condition held before the time ran out
     */
    protected static boolean awaitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.call() && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        return condition.call();
    }
}
