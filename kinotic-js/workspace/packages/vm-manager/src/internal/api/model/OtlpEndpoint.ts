import type { OtlpSignal } from '@/internal/api/model/OtlpSignal'

/**
 * The OTLP endpoint a node issued to one workload: where the node's shipper listens for that
 * workload's telemetry, which signals it accepts there, and the credential its guest must
 * present. All of it holds for the workload's life, since the guest's environment is set when
 * its VM is created.
 */
export interface OtlpEndpoint {

    /** Host address the receiver is bound to, which is where a guest's connection lands. */
    listenAddress: string

    /** Host port the receiver listens on for this workload alone. */
    port: number

    /** Bearer token the receiver requires on every push; only this workload's guest holds it. */
    token: string

    /** The signals the receiver accepts, being those the node shipped when the endpoint was issued. */
    signals: OtlpSignal[]
}
