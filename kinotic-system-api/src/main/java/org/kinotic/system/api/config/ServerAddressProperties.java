package org.kinotic.system.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * How deployed workloads reach one of the platform's servers: the address a workload dials, and the one
 * destination its egress policy always permits. A server has no advertised address of its own, so every
 * deployment configures it.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServerAddressProperties {

    /**
     * Host the workload dials ({@code KINOTIC_SERVER_HOST} in the guest). An IPv4 address or a hostname,
     * on either provider.
     */
    @NotBlank
    private String host;

    /**
     * Port the workload dials.
     */
    private int port;

    /**
     * Whether the workload dials the server over TLS.
     */
    private boolean useSsl = false;

}
