package org.kinotic.core.api.security;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.context.storage.ContextLocal;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Provides access to the security state associated with the current Vert.x context: the
 * authenticated {@link Participant} and an "elevated access" flag that internal code can
 * use to bypass scope enforcement (e.g. when a cache loader needs to read an
 * OrganizationScoped entity on behalf of an APPLICATION-scoped caller).
 * <p>
 * Must only be used as a Spring-managed bean. The underlying {@link ContextLocal}s must be
 * registered before any {@link Vertx} instance is created, which is handled by the bean
 * definitions in {@code org.kinotic.core.internal.config.KinoticVertxConfig}.
 */
@Component
public class SecurityContext {

    private static final ContextLocal<Participant> PARTICIPANT_LOCAL = ContextLocal.registerLocal(Participant.class);
    private static final ContextLocal<Boolean> ELEVATED_ACCESS_LOCAL = ContextLocal.registerLocal(Boolean.class);

    /**
     * Returns the {@link Participant} for the current Vert.x context, or null if none is set.
     */
    public Participant currentParticipant() {
        Context context = Vertx.currentContext();
        if (context != null) {
            return context.getLocal(PARTICIPANT_LOCAL);
        }
        return null;
    }

    /**
     * Sets the {@link Participant} on the given Vert.x context.
     */
    public void setParticipant(Context context, Participant participant) {
        context.putLocal(PARTICIPANT_LOCAL, participant);
    }

    /**
     * Returns {@code true} if the current execution is running with elevated access,
     * meaning scope enforcement (e.g. org checks on OrganizationScoped entities) should
     * be skipped. Used by internal infrastructure such as cache loaders that need to
     * resolve metadata owned by an ORGANIZATION on behalf of an APPLICATION participant.
     */
    public boolean isElevatedAccess() {
        Context context = Vertx.currentContext();
        if (context != null) {
            Boolean elevated = context.getLocal(ELEVATED_ACCESS_LOCAL);
            return elevated != null && elevated;
        }
        return false;
    }

    /**
     * Returns the {@link Participant#getAuthScopeId()} if the current Vert.x context
     * has a participant authenticated under {@code scopeType}.
     *
     * @throws IllegalStateException if no participant is bound to the current context
     * @throws AuthorizationException if the participant's scope type doesn't match
     */
    public String requireAuthScope(AuthScopeType scopeType) {
        Participant participant = requireParticipant();
        if (!scopeType.name().equals(participant.getAuthScopeType())) {
            throw new AuthorizationException(
                    "Auth scope " + scopeType + " required but was '"
                    + participant.getAuthScopeType() + "'");
        }
        return participant.getAuthScopeId();
    }

    /**
     * Verifies the current Vert.x context has a participant authenticated under
     * {@code (scopeType, scopeId)}. Used by services that take the scope as a method
     * parameter (e.g. via {@code @Scope}) and need to confirm the caller's session
     * matches the requested scope.
     *
     * @throws IllegalStateException if no participant is bound to the current context
     * @throws AuthorizationException if the scope type or id doesn't match
     */
    public void requireAuthScope(AuthScopeType scopeType, String scopeId) {
        String actualId = requireAuthScope(scopeType);
        if (!actualId.equals(scopeId)) {
            throw new AuthorizationException(
                    "Caller's " + scopeType + " scope id does not match requested '" + scopeId + "'");
        }
    }

    private Participant requireParticipant() {
        Participant participant = currentParticipant();
        if (participant == null) {
            throw new IllegalStateException(
                    "No Participant is bound to the current Vert.x context");
        }
        return participant;
    }

    /**
     * Executes the given async operation with elevated access. Scope enforcement is
     * disabled before the supplier runs and re-enabled when the returned future completes
     * (whether successfully or exceptionally).
     *
     * @param supplier produces the async operation to run with elevated access
     * @return the future produced by the supplier
     */
    public <T> CompletableFuture<T> withElevatedAccess(Supplier<CompletableFuture<T>> supplier) {
        Context ctx = Vertx.currentContext();
        if (ctx != null) {
            ctx.putLocal(ELEVATED_ACCESS_LOCAL, true);
        }
        return supplier.get().whenComplete((result, err) -> {
            if (ctx != null) {
                ctx.putLocal(ELEVATED_ACCESS_LOCAL, false);
            }
        });
    }

}
