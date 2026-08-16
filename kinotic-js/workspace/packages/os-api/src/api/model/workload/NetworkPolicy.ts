import { NetworkMode } from '@/api/model/workload/NetworkMode'

/**
 * The network access granted to a workload's VM. The workload's own image and entrypoint
 * have no say in it: the policy is applied by the VM provider when the VM is created,
 * which is what makes it safe to run code the platform does not trust.
 */
export class NetworkPolicy {

    /**
     * Whether the VM has network access at all.
     */
    public mode: NetworkMode = NetworkMode.ENABLED

    /**
     * The outbound destinations the VM may reach, as hostnames. Applies only when
     * mode is ENABLED; empty grants the provider's unrestricted egress.
     */
    public allowedHosts: string[] = []

}
