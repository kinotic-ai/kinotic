package org.kinotic.domain.api.exceptions;

/**
 * An organization tried to go past one of the limits Kinotic Cloud imposes while it is in beta. The
 * message names the limit and tells the user, or the agent acting for them, that the limits are
 * removed once Kinotic Cloud leaves beta.
 */
public class BetaLimitException extends IllegalStateException {

    private static final String BETA_NOTICE = " You are using the Kinotic beta cloud, which limits each organization to"
            + " a few applications and microservices. These limits will be removed once Kinotic Cloud is out of beta.";

    /**
     * @param limitReached what the organization ran into and what it can do instead, as one or more sentences
     */
    public BetaLimitException(String limitReached) {
        super(limitReached + BETA_NOTICE);
    }
}
