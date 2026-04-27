package org.kinotic.test.support.kinotic;

import io.vertx.core.Vertx;
import org.kinotic.core.api.security.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

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
     * Elasticsearch routing keys and ID prefixes from this value when running
     * with elevated access, so all test entities are co-located under one org.
     */
    public static final String TEST_ORG_ID = "kinotic";

    @Autowired
    protected Vertx vertx;

    @Autowired
    protected SecurityContext securityContext;

    /**
     * Runs the supplied async operation on a Vert.x context with elevated access
     * so that {@code AbstractCrudService} skips org-scope enforcement on
     * {@link org.kinotic.os.api.model.OrganizationScoped} entities. This lets tests
     * exercise scoped services without authenticating as an ORGANIZATION participant.
     */
    protected <T> CompletableFuture<T> elevated(Supplier<CompletableFuture<T>> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        vertx.getOrCreateContext().runOnContext(v ->
                securityContext.withElevatedAccess(supplier)
                               .whenComplete((value, error) -> {
                                   if (error != null) {
                                       result.completeExceptionally(error);
                                   } else {
                                       result.complete(value);
                                   }
                               }));
        return result;
    }
}
