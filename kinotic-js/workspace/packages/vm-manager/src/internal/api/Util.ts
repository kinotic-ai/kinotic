/**
 * Static utility functions used across the vm-manager.
 */
export class Util {

    /**
     * Whether a workload's image must be pulled before every start. A reference pinning a
     * digest or a tag other than {@code latest} names one immutable image, so the copy the
     * node already holds is that image. A floating reference — no tag, or {@code latest} —
     * names whatever the registry holds now, and only a pull answers that. This is the rule
     * Kubernetes applies as its default imagePullPolicy.
     */
    public static mustPullBeforeStart(reference: string): boolean {
        let ret: boolean
        if (reference.includes('@')) {
            ret = false
        } else {
            // A colon after the last slash separates the tag; one before it belongs to a registry port
            const colon = reference.lastIndexOf(':')
            const tag = colon > reference.lastIndexOf('/') ? reference.slice(colon + 1) : null
            ret = tag === null || tag === 'latest'
        }
        return ret
    }
}
