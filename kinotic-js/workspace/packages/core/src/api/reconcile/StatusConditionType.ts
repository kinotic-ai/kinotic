/**
 * What a watcher can infer about a record without its authority confirming it. Each value documents
 * who sets it and who clears it.
 */
export enum StatusConditionType {
    /**
     * The node hosting the record has not answered: its heartbeat is overdue, or a call to it could
     * not be delivered and may or may not have executed. What the record says of its run is the last
     * the node reported, not what the node is doing now. Set by the orchestrator; cleared by the
     * node's next report of the record.
     */
    NODE_UNREACHABLE = 'NODE_UNREACHABLE'
}
