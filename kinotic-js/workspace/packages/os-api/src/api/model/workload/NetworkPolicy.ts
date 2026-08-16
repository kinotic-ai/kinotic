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
     * The outbound destinations the VM may reach, as hostnames. A populated list is enforced
     * on the connection, so an unlisted destination is unreachable by name and by raw IP
     * alike. An empty list is not a denial: it grants unrestricted egress.
     */
    public allowedHosts: string[] = []

}
