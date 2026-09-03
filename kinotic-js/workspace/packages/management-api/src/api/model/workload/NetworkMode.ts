
export enum NetworkMode {

    ENABLED = 'ENABLED',

    /**
     * No network access. Nothing outside the VM is reachable, by name or by address, and
     * any NetworkPolicy.allowedHosts the workload declares does not apply.
     */
    DISABLED = 'DISABLED'
}
