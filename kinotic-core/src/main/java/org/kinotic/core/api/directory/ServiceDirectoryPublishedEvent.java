package org.kinotic.core.api.directory;

import java.util.Collection;

/**
 * Published once entries this node registered are stored in the directory and visible to its queries: the
 * entries of every service registered while the context started, as one event on
 * {@code ApplicationReadyEvent}, and the entry of a service registered later, as an event of its own. A
 * listener that derives something from the whole directory, such as the authorization model generated from
 * every published contract, queries the directory when it receives this.
 *
 * @param directory the directory the entries are stored in
 * @param entries   the entries this node stored
 */
public record ServiceDirectoryPublishedEvent(ServiceDirectory directory, Collection<ServiceDirectoryEntry> entries) {
}
