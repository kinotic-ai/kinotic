/**
 * The OTLP endpoint a node issued to one workload: where the node's shipper listens for that
 * workload's traces, and the credential its guest must present there. Both hold for the
 * workload's life, since the guest's environment is set when its VM is created.
 */
export interface TraceEndpoint {

    /** Host address the receiver is bound to, which is where a guest's connection lands. */
    listenAddress: string

    /** Host port the receiver listens on for this workload alone. */
    port: number

    /** Bearer token the receiver requires on every push; only this workload's guest holds it. */
    token: string
}
