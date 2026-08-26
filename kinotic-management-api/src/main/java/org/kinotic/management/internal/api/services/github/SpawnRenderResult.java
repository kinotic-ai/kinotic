package org.kinotic.management.internal.api.services.github;

import java.util.Map;

/**
 * The outcome of a {@link GraalJsSpawnRenderer#render} call: the rendered files
 * and, for each, the originating template path. {@code sources} lets the caller
 * trace a rendered file back to its template entry to recover source metadata
 * (e.g. the executable bit) that the rendered destination path alone cannot
 * convey.
 *
 * @param files   rendered file contents keyed by destination path
 * @param sources originating template path keyed by the same destination path
 */
public record SpawnRenderResult(Map<String, String> files, Map<String, String> sources) {
}
