/** What kind of thing needs an operator; the attention list groups its items by it. */
export enum AttentionKind {
    FAILED_RUN = 'failed-run',
    FAILED_WORKLOAD = 'failed-workload',
    WORKLOAD_ON_SILENT_NODE = 'workload-on-silent-node',
    UNREACHABLE_NODE = 'unreachable-node',
    DRAINING_NODE = 'draining-node',
    VERSION_SKEW = 'version-skew'
}
