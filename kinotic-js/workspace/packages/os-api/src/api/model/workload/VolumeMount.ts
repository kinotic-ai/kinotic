/**
 * A host directory exposed inside a workload's guest VM.
 */
export interface VolumeMount {

    /** Absolute path on the host to mount into the guest. */
    hostPath: string

    /** Absolute path inside the guest where the host path is mounted. */
    guestPath: string

    /** When true, the mount is exposed read-only inside the guest. Defaults to false. */
    readOnly?: boolean

    /**
     * Hard cap in megabytes on what the guest may write through this mount. Unset leaves the
     * mount bounded only by the host filesystem it comes from. Applies to writable mounts,
     * since a read-only mount is already unwritable.
     */
    sizeLimitMb?: number
}
