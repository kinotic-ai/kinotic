package org.kinotic.core.api.security;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.context.storage.ContextLocal;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

/**
 * Provides access to the security state associated with the current Vert.x context: the
 * authenticated {@link Participant}.
 * <p>
 * Must only be used as a Spring-managed bean. The underlying {@link ContextLocal} must be
 * registered before any {@link Vertx} instance is created, which is handled by the bean
 * definitions in {@code org.kinotic.core.internal.config.KinoticVertxConfig}.
 */
@Component
@RequiredArgsConstructor
public class SecurityContext {

    private static final ContextLocal<Participant> PARTICIPANT_LOCAL = ContextLocal.registerLocal(Participant.class);

    private final SecurityExceptionFactory securityExceptions;

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
     * Returns the current {@link Participant} narrowed to {@code type}.
     *
     * @param type the required {@code Participant} subtype (e.g.
     *             {@code OrganizationParticipant.class})
     * @throws IllegalStateException if no participant is bound to the current Vert.x context
     * @throws AuthorizationException if the current participant is not an instance of {@code type}
     */
    public <T extends Participant> T requireParticipant(Class<T> type) {
        Participant participant = currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        if (!type.isInstance(participant)) {
            throw securityExceptions.notAuthorized(Level.ERROR, "Participant type mismatch: {} required, got {} (participant id={})",
                                                   type.getSimpleName(), participant.getClass().getSimpleName(), participant.getId());
        }
        return type.cast(participant);
    }

    /**
     * Sets the {@link Participant} on the given Vert.x context.
     */
    public void setParticipant(Context context, Participant participant) {
        context.putLocal(PARTICIPANT_LOCAL, participant);
    }

}
