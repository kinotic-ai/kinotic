package org.kinotic.system.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for deploying customer project workloads from GitHub pushes.
 * Accessible via {@code kinotic.systemApi.deployment.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class DeploymentProperties {

    /**
     * OCI image the sync and runtime workloads run — the workload-runner image holding the
     * checkout/sync entrypoint and the microservice supervisor.
     */
    private String workloadRunnerImage = "kinoticai/workload-runner:latest";

    /**
     * How the sync workload reaches the org server, which it synchronizes the project's entity
     * definitions and reports the project's artifacts through.
     */
    @Valid
    private ServerAddressProperties orgServer = new ServerAddressProperties().setPort(58503);

    /**
     * How a microservice's runtime workload reaches the app server, which it publishes the
     * project's services through.
     */
    @Valid
    private ServerAddressProperties appServer = new ServerAddressProperties().setPort(58505);

    /**
     * Destinations (IPv4 addresses, CIDRs, or hostnames) the sync workload may reach beyond
     * the org server — the repository and package registry hosts, so {@code git fetch} and
     * {@code bun install} work on nodes that deny workload egress by default.
     */
    private List<String> syncAllowedHosts = new ArrayList<>();

    /**
     * Destinations (IPv4 addresses, CIDRs, or hostnames) the runtime workload may reach
     * beyond the app server.
     */
    private List<String> runtimeAllowedHosts = new ArrayList<>();

}
