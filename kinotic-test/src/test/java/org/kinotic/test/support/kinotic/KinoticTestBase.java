package org.kinotic.test.support.kinotic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzModelGenerator;
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
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.core.api.security.RequestAuthorizer;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
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
import java.util.UUID;
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
     * The organization the test fixtures belong to: the one the test stack's migrations seed, so a service that
     * requires the organization to exist finds it. Scoped CRUD services derive Elasticsearch routing keys and id
     * prefixes from this value, so all test entities are co-located under one organization.
     */
    public static final String TEST_ORG_ID = "test-org";

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

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private AuthzStoreRepository authzStores;

    @Autowired
    private ParticipantIdentityService participantIdentities;

    /**
     * Waits for the system store to run the model of every system service in the directory, and makes
     * {@link #TEST_ORGANIZATION_PARTICIPANT} a member and the administrator of {@link #TEST_ORG_ID} in it, so the
     * listings that filter to what the caller may see show it everything, as they do an organization's administrator.
     */
    @BeforeEach
    public void bindTestParticipantAsAdministrator() throws Exception {
        if (!testParticipantAdministers) {
            // the first context of a run publishes every contract in one write and the reconciler then takes the
            // model they imply; a check for a permission the store's model lacks fails rather than refuses
            assertTrue(awaitUntil(this::systemModelCurrent), "the system store never ran the model of the system's services");
            // OrganizationGraphBootstrap places the seeded organization on the system, which the tests' checks reach it through
            assertTrue(awaitUntil(this::placed), "the organization graph bootstrap never placed the test organization");
            assertTrue(awaitUntil(this::bound), "the system store never accepted the test participant's administrator binding");
            testParticipantAdministers = true;
        }
    }

    private boolean systemModelCurrent() throws Exception {
        List<ServiceDefinition> services = await(serviceDirectory.findSystemDefinitions());
        AuthzStore system = await(authzStores.findById(AuthzStore.SYSTEM));
        AuthzModelRevision observed = system != null ? system.getState().getObserved() : null;
        return !services.isEmpty() && observed != null && observed.hash().equals(modelGenerator.systemModel(services).hash());
    }

    private boolean placed() throws Exception {
        String organization = AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID);
        return await(relationshipService.read(AuthzStoreService.SYSTEM, organization))
                .contains(AuthzGraphUtil.containment(AuthzConstants.SYSTEM_OBJECT, organization));
    }

    private boolean bound() {
        String member = AuthzGraphUtil.authzUser(TEST_ORGANIZATION_PARTICIPANT.getId());
        String organization = AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID);
        boolean ret;
        try {
            await(relationshipService.ensure(AuthzStoreService.SYSTEM,
                                             List.of(new RelationshipTuple(member, AuthzConstants.MEMBER_RELATION, organization)))
                                     .compose(v -> relationshipService.ensureBound(AuthzStoreService.SYSTEM,
                                                                                   TEST_PARTICIPANT_ADMIN_BINDING,
                                                                                   AuthzConstants.ORGANIZATION_ADMIN_ROLE,
                                                                                   member,
                                                                                   organization)));
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

    /** The result of {@code future}, waited for as long as a test's call to a service may take. */
    protected static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    /** Eight random hex characters, which make a fixture's name or id a test's own. */
    protected static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * Creates a user of an organization, as signing up or adding a member does.
     *
     * @param organizationId the organization the user belongs to
     * @param emailPrefix    the start of the user's email, which a random suffix makes unique
     * @return the organization's user
     */
    protected OrganizationParticipantIdentity member(String organizationId, String emailPrefix) throws Exception {
        OrganizationParticipantIdentity user = new OrganizationParticipantIdentity();
        user.setOrganizationId(organizationId)
            .setEmail(emailPrefix + "-" + suffix() + "@kinotic.test");
        user.setDisplayName(emailPrefix);
        return await(participantIdentities.createUser(user, "Member-1"));
    }

    /**
     * Creates a system user, one of the system's staff.
     *
     * @param emailPrefix the start of the user's email, which a random suffix makes unique
     * @return the system user
     */
    protected SystemParticipantIdentity systemUser(String emailPrefix) throws Exception {
        SystemParticipantIdentity user = new SystemParticipantIdentity();
        user.setEmail(emailPrefix + "-" + suffix() + "@kinotic.test");
        user.setDisplayName(emailPrefix);
        return await(participantIdentities.createUser(user, "Member-1"));
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
        await(requestAuthorizer.authorize(cri(service, function, caller), caller, EventConstants.CONTENT_TYPE_JSON,
                                          jsonMapper.writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Authorizes a tool call as the MCP endpoint does, with the function's arguments by parameter name.
     *
     * @throws ExecutionException caused by an {@link AuthorizationException} when the caller is refused
     */
    protected void authorizeToolCall(String service, String function, Participant caller, Map<String, ?> arguments) throws Exception {
        await(requestAuthorizer.authorize(cri(service, function, caller), caller, jsonMapper.<ObjectNode>valueToTree(arguments)));
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
        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause(), String.valueOf(failure.getCause()));
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
        ServiceDirectoryEntry entry = await(serviceDirectory.findEntry(service));
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
