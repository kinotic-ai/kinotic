/**
 * How far a node may deviate from the configuration the platform ships and tests.
 *
 * A node is {@link NodeMode.PRODUCTION} unless it declares otherwise, so a deployment that
 * never sets the variable runs only paths that have been tested.
 */
export enum NodeMode {

    /** Only the configuration the platform ships and tests. */
    PRODUCTION = 'PRODUCTION',

    /**
     * Permits accommodations a developer machine needs and a real deployment must not rely on,
     * such as the network namespace anchor that gives a workload a NIC on a host whose
     * hypervisor cannot hot-plug one.
     */
    DEVELOPMENT = 'DEVELOPMENT'
}
