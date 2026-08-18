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
     * The outbound destinations the VM may reach, including the api-gateway the workload is
     * deployed to talk to. Applies only when mode is ENABLED.
     *
     * What an entry may be, and what an empty list means, follow the provider of the node the
     * workload is placed on. CLOUD_HYPERVISOR takes IPv4 addresses and CIDRs, enforced by the
     * node's firewall, and reaches nothing beyond name resolution when the list is empty.
     * BOXLITE takes hostnames, enforced on the connection so an unlisted destination is
     * unreachable by name and by raw IP alike, and grants unrestricted egress when the list
     * is empty.
     */
    public allowedHosts: string[] = []

}
