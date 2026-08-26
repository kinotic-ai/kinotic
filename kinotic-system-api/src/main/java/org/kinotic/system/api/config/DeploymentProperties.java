package org.kinotic.system.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for deploying customer project workloads from GitHub pushes.
 * Accessible via {@code kinotic.orchestrator.deployment.*}
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
    private String workloadRunnerImage = "ghcr.io/kinotic-ai/workload-runner:latest";

    /**
     * Host the deployed workloads use to reach the api-gateway ({@code KINOTIC_SERVER_HOST}
     * in the guest). There is no advertised-address the server knows about itself, so
     * deployments must configure how workloads reach it.
     */
    @NotBlank
    private String serverHost;

    /**
     * Port the deployed workloads use to reach the api-gateway.
     */
    private int serverPort = 58503;

    /**
     * Whether the deployed workloads reach the api-gateway over TLS.
     */
    private boolean serverUseSsl = false;

    /**
     * Destinations (IPv4 addresses or CIDRs) the sync workload may reach beyond the
     * gateway — the repository host's address ranges, so {@code git fetch} works on nodes
     * that deny workload egress by default.
     */
    private List<String> syncAllowedHosts = new ArrayList<>();

    /**
     * Destinations (IPv4 addresses or CIDRs) the runtime workload may reach beyond the
     * gateway.
     */
    private List<String> runtimeAllowedHosts = new ArrayList<>();

    /**
     * Destinations (IPv4 addresses or CIDRs) that resolve the gateway itself, granted to
     * both workloads on top of the lists above.
     */
    private List<String> serverAllowedHosts = new ArrayList<>();

    /**
     * Memory of the sync workload's VM in megabytes. It compiles the project's entity
     * sources during {@code kinotic sync}, which needs more headroom than serving does.
     */
    private int syncMemoryMb = 2048;

    /**
     * Size limit applied to the project checkout mount (clone plus node_modules), enforced
     * by the node's filesystem quota.
     */
    private int syncMountLimitMb = 4096;

    /**
     * Memory of the runtime workload's VM in megabytes.
     */
    private int runtimeMemoryMb = 1024;

}
