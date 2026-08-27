package org.kinotic.grind.internal.api.model;

/**
 * The completed steps of a run being resumed, keyed by step path. A {@link Step} consults
 * the ledger before executing: a present entry means the step already completed in the
 * original run and should be replayed, reloaded, or skipped according to its store contract.
 */
public interface ReplayLedger {

    /**
     * Finds the entry for the given step path.
     * @param stepPath the {@code /} separated sequence path of the step
     * @return the {@link ReplayEntry} or null if the step did not complete in the original run
     */
    ReplayEntry get(String stepPath);

}
