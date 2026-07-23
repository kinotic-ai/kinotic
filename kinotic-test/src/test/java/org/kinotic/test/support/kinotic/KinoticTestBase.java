package org.kinotic.test.support.kinotic;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.persistence.internal.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Test base for tests that need the full Kinotic stack (Elasticsearch + kinotic-server)
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

    @Autowired
    protected Vertx vertx;

    @Autowired
    protected SecurityContext securityContext;

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
    protected <T> CompletableFuture<T> runAsOrganization(Supplier<CompletableFuture<T>> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            securityContext.setParticipant(context, TEST_ORGANIZATION_PARTICIPANT);
            try {
                supplier.get().whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
            } catch (Throwable t) {
                // a service that throws synchronously would otherwise leave result uncompleted, hanging the test
                result.completeExceptionally(t);
            }
        });
        return result;
    }
}
