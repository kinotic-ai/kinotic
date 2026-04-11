package org.kinotic.core.api.security;

import io.vertx.core.Context;
import io.vertx.core.ContextLocal;
import io.vertx.core.Vertx;

/**
 * Provides access to the {@link Participant} associated with the current Vert.x context.
 * The {@link Participant} is set by the service invocation infrastructure before a service method is called,
 * and is available for the duration of the invocation including nested method calls and {@code vertx.executeBlocking()} blocks.
 *
 * Created by Claude on 2026-04-11.
 */
public final class ParticipantContext {

    private static final ContextLocal<Participant> PARTICIPANT_LOCAL = ContextLocal.registerLocal(Participant.class);

    private ParticipantContext() {}

    /**
     * Returns the {@link Participant} for the current Vert.x context, or null if none is set.
     *
     * @return the current {@link Participant} or null
     */
    public static Participant currentParticipant() {
        Context context = Vertx.currentContext();
        if (context != null) {
            return context.getLocal(PARTICIPANT_LOCAL);
        }
        return null;
    }

    /**
     * Sets the {@link Participant} on the current Vert.x context.
     *
     * @param participant the {@link Participant} to set
     */
    public static void setCurrentParticipant(Participant participant) {
        Context context = Vertx.currentContext();
        if (context != null) {
            context.putLocal(PARTICIPANT_LOCAL, participant);
        }
    }

}
