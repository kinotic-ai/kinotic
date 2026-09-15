package org.kinotic.auth.api.engine;

/**
 * An ABAC policy engine: compiles ABAC policy expressions for actions and decides whether
 * individual requests are permitted by them.
 * <p>
 * Policies are registered once per action (typically at service-registration time) via
 * {@link #registerPolicy}; each request is then evaluated with {@link #isAuthorized}.
 * Implementations target a specific evaluation backend (e.g. Cedar, jCasbin) but share this
 * contract, so the same ABAC expression and request can be evaluated against any of them.
 */
public interface AuthorizationEngine {

    /**
     * Compiles an ABAC policy expression for an action and retains it for evaluation.
     * Registering an action that already has a policy replaces the previous one.
     *
     * @param action     the action the policy guards (e.g. {@code "placeOrder"})
     * @param expression the ABAC policy expression (e.g. {@code "participant.roles contains 'finance'"})
     * @throws RuntimeException if the expression cannot be parsed or compiled
     */
    void registerPolicy(String action, String expression);

    /**
     * Evaluates a request against the policy registered for its action.
     *
     * @param request the request to authorize
     * @return {@code true} if the request is permitted, {@code false} if denied
     * @throws RuntimeException if no policy is registered for the action, or evaluation fails
     */
    boolean isAuthorized(AuthorizationRequest request);

    /**
     * Returns whether a policy is registered for the given action.
     */
    boolean hasPolicy(String action);

    /**
     * Removes the policy registered for the given action, if any.
     */
    void removePolicy(String action);
}
