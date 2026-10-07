package org.kinotic.management.internal.api.services.telemetry;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base of the tests that call a published service as a participant: an organization user of
 * acme, a platform operator granted the telemetry of every organization on the platform, and a
 * platform participant granted nothing, each call run on a Vert.x context with the participant
 * bound, mirroring how the gateway invokes published services.
 */
abstract class ParticipantCallTest {

    protected static final Participant ACME_USER =
            new DefaultOrganizationParticipant("user-1", "acme", Map.of(), List.of("USER"));
    protected static final Participant PLATFORM_OPERATOR =
            new DefaultSystemParticipant("operator-1", Map.of(), List.of("ADMIN"));
    protected static final Participant PLATFORM_NEWCOMER =
            new DefaultSystemParticipant("newcomer-1", Map.of(), List.of());

    protected static SecurityContext securityContext;
    protected static TenantAccess tenantAccess;
    protected static Vertx vertx;

    @BeforeAll
    static void startVertx() {
        // SecurityContext registers its ContextLocal at class load, which must happen
        // before any Vertx instance is created
        SecurityExceptionFactory securityExceptions = new SecurityExceptionFactory(new KinoticProperties());
        securityContext = new SecurityContext(securityExceptions);
        AuthzStoreService stores = mock(AuthzStoreService.class);
        when(stores.modelId(AuthzStoreService.PLATFORM)).thenReturn(Future.succeededFuture("model-1"));
        RelationshipService relationships = mock(RelationshipService.class);
        // the operator holds what it is asked about on the platform, the newcomer nothing
        when(relationships.check(eq(AuthzStoreService.PLATFORM), eq("model-1"), any(), any()))
                .thenAnswer(call -> Future.succeededFuture(call.<RelationshipTuple>getArgument(2).user().equals("user:" + PLATFORM_OPERATOR.getId())));
        tenantAccess = new TenantAccess(securityContext, stores, relationships, securityExceptions);
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() {
        vertx.close();
    }

    protected <T> T callAs(Participant participant, Supplier<Future<T>> call) throws Throwable {
        Promise<T> result = Promise.promise();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(unused -> {
            securityContext.setParticipant(context, participant);
            try {
                call.get().onComplete(result);
            } catch (Throwable error) {
                result.fail(error);
            }
        });
        // await rethrows a failed future's raw cause, so failureOf sees the unwrapped exception
        return result.future().await(10, TimeUnit.SECONDS);
    }

    protected <T> Throwable failureOf(Participant participant, Supplier<Future<T>> call) {
        try {
            callAs(participant, call);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }
}
