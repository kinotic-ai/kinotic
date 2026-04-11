package org.kinotic.core.api.security;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.context.storage.ContextLocal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Provides access to the {@link Participant} associated with the current Vert.x context.
 * The {@link Participant} is set by the service invocation infrastructure before a service method is called,
 * and is available for the duration of the invocation including nested method calls and {@code vertx.executeBlocking()} blocks.
 * <p>
 * This class must only be used as a Spring-managed bean. The underlying {@link ContextLocal} must be registered
 * before the Vert.x instance is created, which is handled by the bean definition in
 * {@code org.kinotic.core.internal.config.KinoticVertxConfig}. Creating instances manually will not work correctly.
 *
 * Created by Navíd Mitchell on 2026-04-11.
 */
@Component
public class ParticipantContext {

    private final ContextLocal<Participant> participantLocal;

    public ParticipantContext(@Qualifier("participantContextLocal") ContextLocal<Participant> participantLocal) {
        this.participantLocal = participantLocal;
    }

    /**
     * Returns the {@link Participant} for the current Vert.x context, or null if none is set.
     *
     * @return the current {@link Participant} or null
     */
    public Participant currentParticipant() {
        Context context = Vertx.currentContext();
        if (context != null) {
            return context.getLocal(participantLocal);
        }
        return null;
    }

    /**
     * Sets the {@link Participant} on the given Vert.x context.
     *
     * @param context the Vert.x {@link Context}
     * @param participant the {@link Participant} to set
     */
    public void setParticipant(Context context, Participant participant) {
        context.putLocal(participantLocal, participant);
    }

}
