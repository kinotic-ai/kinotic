package org.kinotic.github.internal.api.services;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * Snapshot of what the SPA staged when it asked for a GitHub install URL. Carried
 * through the install round-trip via the {@code state} query parameter. After the
 * user finishes the install, the SPA presents the same {@code state} back to
 * {@code completeInstall()}, which atomically pops this record from the install-state
 * store. {@code intent} and {@code returnTo} let the SPA decide what to do after the
 * install completes (e.g. re-open the new-project sidebar).
 *
 * <p>Implements {@link Serializable} so Ignite can ship the value across cluster nodes.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class StagedInstall {

    private String organizationId;

    /** Path the SPA wants to land back on after the install completes. May carry
     *  query params (e.g. {@code /projects?openNewProject=1}) so the destination
     *  page can pick up where the user was. */
    private String returnTo;
}
