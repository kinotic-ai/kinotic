package org.kinotic.domain.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * One entry of the ledger: what happened to a watched record, from where, and why. The record itself
 * says what it is now; its entries say how it got there.
 *
 * @param timestamp    when the write landed, the data stream's time field
 * @param type         the kind of record that changed
 * @param id           the record's id
 * @param scope        the scope the record is addressed under, as its repository's {@code scopeOf} gives
 *                     it: its organization for a record an organization owns, null for a node
 * @param parent       what the record belongs to, null for a record the platform made on its own
 * @param kind         what happened
 * @param source       what caused it: the node that reported, the operator, the orchestrator's inference
 * @param serverNodeId the server node that wrote it
 * @param generation   the record's generation when written, null for a record that carries none
 * @param message      why, for an operator
 * @param value        what was written, as an object
 */
public record WatchEvent(@JsonProperty("@timestamp") Date timestamp,
                         WatchedType type,
                         String id,
                         String scope,
                         WatchedParent parent,
                         WatchEventKind kind,
                         String source,
                         String serverNodeId,
                         Long generation,
                         String message,
                         Object value) {
}
