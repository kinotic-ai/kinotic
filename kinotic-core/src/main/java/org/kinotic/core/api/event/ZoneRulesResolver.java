package org.kinotic.core.api.event;

import io.vertx.core.Future;
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
     * @return a future completing with the participant's zone rules, or failing with an
     *         {@link IllegalArgumentException} when the participant is of a type this resolver has no rules for, or
     *         one of its ids cannot form a valid zone label
     */
    Future<ZoneRules> resolve(Participant participant);
}
