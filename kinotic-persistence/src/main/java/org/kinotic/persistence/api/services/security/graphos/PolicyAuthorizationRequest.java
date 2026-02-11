package org.kinotic.persistence.api.services.security.graphos;

public interface PolicyAuthorizationRequest {

    /**
     * Retrieves the name of the policy being evaluated.
     *
     * @return The policy name as a String.
     */
    String policy();

    /**
     * Marks the policy as authorized, indicating that it passed the evaluation.
     */
    void authorize();

    /**
     * Marks the policy as denied, indicating that it failed the evaluation.
     */
    void deny();

    /**
     * Checks if the policy has been authorized.
     *
     * @return True if the policy is authorized, otherwise false.
     */
    boolean isAuthorized();
}
