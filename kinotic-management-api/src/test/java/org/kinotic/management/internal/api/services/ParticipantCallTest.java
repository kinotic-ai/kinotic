package org.kinotic.management.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Base of the tests that call a published service as a participant: an organization user of
 * acme and a platform operator, each call run on a Vert.x context with the participant bound,
 * mirroring how the gateway invokes published services.
 */
abstract class ParticipantCallTest {

    protected static final Participant ACME_USER =
            new DefaultOrganizationParticipant("user-1", "acme", Map.of(), List.of("USER"));
    protected static final Participant PLATFORM_OPERATOR =
            new DefaultSystemParticipant("operator-1", Map.of(), List.of("ADMIN"));

    protected static SecurityContext securityContext;
    protected static TenantAccess tenantAccess;
    protected static Vertx vertx;

    @BeforeAll
    static void startVertx() {
        // SecurityContext registers its ContextLocal at class load, which must happen
        // before any Vertx instance is created
        securityContext = new SecurityContext();
        tenantAccess = new TenantAccess(securityContext);
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
