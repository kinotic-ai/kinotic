
export enum NetworkMode {

    ENABLED = 'ENABLED',

    /**
     * No network access. The boxlite provider cannot boot a VM in this mode and rejects a
     * workload that asks for it; restrict egress with NetworkPolicy.allowedHosts instead.
     */
    DISABLED = 'DISABLED'
}
