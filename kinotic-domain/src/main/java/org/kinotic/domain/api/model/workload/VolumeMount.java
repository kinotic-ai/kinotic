package org.kinotic.domain.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A host directory exposed inside a {@link Workload}'s guest VM.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VolumeMount {

    /**
     * Absolute path on the host to mount into the guest.
     */
    private String hostPath;

    /**
     * Absolute path inside the guest where the host path is mounted.
     */
    private String guestPath;

    /**
     * When true, the mount is exposed read-only inside the guest. Defaults to false.
     */
    private boolean readOnly = false;
}
