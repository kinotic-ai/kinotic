package org.kinotic.domain.api.exceptions;

/**
 * An organization tried to go past one of the limits Kinotic Cloud imposes while it is in beta. The
 * message names the limit and tells the user, or the agent acting for them, that the limits are
 * lifted by a paid plan once Kinotic Cloud launches.
 */
public class BetaLimitException extends IllegalStateException {

    private static final String BETA_NOTICE = " You're building on the free Kinotic beta cloud, which includes a set number of"
            + " applications and microservices for each organization. When Kinotic Cloud launches, paid plans will let you"
            + " scale well beyond these limits.";

    /**
     * @param limitReached what the organization ran into and what it can do instead, as one or more sentences
     */
    public BetaLimitException(String limitReached) {
        super(limitReached + BETA_NOTICE);
    }
}
