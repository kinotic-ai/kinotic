package org.kinotic.core.api.reconcile;

/**
 * What the platform can infer about a record without its authority confirming it.
 */
public enum ConditionType {

    /**
     * The node hosting the record has not answered: its heartbeat is overdue, or a call to it could
     * not be delivered and may or may not have executed. What the record says of its run is the last
     * the node reported, not what the node is doing now.
     */
    NODE_UNREACHABLE
}
