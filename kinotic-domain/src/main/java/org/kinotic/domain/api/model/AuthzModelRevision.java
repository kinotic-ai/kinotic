package org.kinotic.domain.api.model;

/**
 * One model an authorization store runs, named by the hash of the model's canonical definition: the shape of
 * both what a store should run and what it does, so an {@link AuthzStore} is in its desired state exactly when
 * the engine runs the model generated from the directory.
 *
 * @param hash the model's hash, as the model generator computes it
 */
public record AuthzModelRevision(String hash) {
}
