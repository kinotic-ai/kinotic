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
     * deployed to talk to. An empty list denies every destination, leaving the VM able to
     * resolve names and reach nothing. Applies only when mode is ENABLED.
     *
     * What an entry may be follows the provider of the node the workload is placed on:
     * CLOUD_HYPERVISOR takes IPv4 addresses and CIDRs, enforced by the node's firewall, and
     * BOXLITE takes hostnames, enforced on the connection so an unlisted destination is
     * unreachable by name and by raw IP alike.
     */
    public allowedHosts: string[] = []

}
