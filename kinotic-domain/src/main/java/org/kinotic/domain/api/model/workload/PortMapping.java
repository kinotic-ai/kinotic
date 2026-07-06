package org.kinotic.domain.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A guest port exposed on the host for a {@link Workload}'s VM.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PortMapping {

    /**
     * Host port to expose. When null, the provider assigns one.
     */
    private Integer hostPort;

    /**
     * Guest port to map to the host.
     */
    private int guestPort;

    /**
     * Transport protocol. When null, the provider's default applies.
     */
    private PortProtocol protocol;

    /**
     * Host interface to bind on. When null, the provider's default applies.
     */
    private String hostIp;
}
