package org.kinotic.domain.api.reconcile;

/**
 * What a watcher can infer about a record without its authority confirming it. Each value documents
 * who sets it and who clears it.
 */
public enum StatusConditionType {

    /**
     * The node hosting the record has not answered: its heartbeat is overdue, or a call to it could
     * not be delivered and may or may not have executed. What the record says of its run is the last
     * the node reported, not what the node is doing now. Set by the orchestrator; cleared by the
     * node's next report of the record.
     */
    NODE_UNREACHABLE,

    /**
     * The server node running the record's execution left the cluster while it ran, so no node
     * will ever report its end: what the record says of the execution is the last that node wrote.
     * Set by the cluster membership watch on every node; never cleared, since the execution does
     * not resume.
     */
    SERVER_NODE_LEFT
}
