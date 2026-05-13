package org.kinotic.gateway.internal.auth;

import java.util.Set;

/**
 * Cookie-key namespace for an OIDC redirect flow. Each handler picks its own prefix so
 * concurrent flows on the same session cookie don't collide (e.g. signup vs login on
 * the same browser).
 *
 * @param state      session key for the OAuth state value
 * @param nonce      session key for the OIDC nonce
 * @param pkce       session key for the PKCE verifier
 * @param configId   session key for the config id used to start the flow
 * @param extraKeys  any additional session keys the handler stashes on start and wants
 *                   the orchestrator to capture into {@link CallbackResult#extras()}
 *                   on callback (e.g. {@code "oidc.orgId"} for the SSO path)
 */
public record SessionKeys(String state,
                          String nonce,
                          String pkce,
                          String configId,
                          Set<String> extraKeys) {

    /**
     * Builds a session-key set under the given dot-separated prefix.
     */
    public static SessionKeys ofPrefix(String prefix, String... extraKeys) {
        return new SessionKeys(
                prefix + ".state",
                prefix + ".nonce",
                prefix + ".pkce",
                prefix + ".configId",
                Set.of(extraKeys));
    }
}
