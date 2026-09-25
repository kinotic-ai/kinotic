package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.WatchEventKind;

/**
 * What a write to a watched record was, for the ledger: the caller's half of a
 * {@link org.kinotic.domain.api.model.WatchEvent}, the rest being read from the record and the server.
 *
 * @param kind    what happened
 * @param source  what caused it
 * @param message why, for an operator
 * @param value   what was written, as an object
 */
public record WatchedChange(WatchEventKind kind, String source, String message, Object value) {
}
