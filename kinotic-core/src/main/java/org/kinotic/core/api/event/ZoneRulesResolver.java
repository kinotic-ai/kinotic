package org.kinotic.core.api.event;

import org.kinotic.core.api.security.Participant;

/**
 * Derives the zones an authenticated {@link Participant} may address. Each server's participant model supplies one,
 * and the gateway authorizes every send and subscription of a connection by the rules it derives for the
 * connection's participant.
 */
@FunctionalInterface
public interface ZoneRulesResolver {

    /**
     * Derives the zone rules for the given participant.
     *
     * @param participant the authenticated participant
     * @return the participant's zone rules
     * @throws IllegalArgumentException when the participant is of a type this resolver has no rules for, or one of
     *                                  its ids cannot form a valid zone label
     */
    ZoneRules resolve(Participant participant);
}
