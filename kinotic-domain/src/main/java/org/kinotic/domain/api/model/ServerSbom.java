package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The libraries one Kinotic server node runs, recorded when the node starts; they cannot change
 * while it runs. One row per node.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServerSbom implements Identifiable<String> {

    /**
     * The node's Ignite id.
     */
    private String id;

    /**
     * The server kind the node runs, such as {@code management}, {@code system} or {@code app}.
     */
    private String serverName;

    /**
     * The Kinotic version the node runs; null when it runs from classes rather than a packaged jar.
     */
    private String version;

    /**
     * The package URL of every library the node's server was built with and of the OpenTelemetry agent
     * it runs with, e.g. {@code pkg:maven/io.vertx/vertx-core@5.1.6}; empty when the node runs from
     * classes rather than a packaged jar.
     */
    private List<String> packages = new ArrayList<>();

    /**
     * When the node recorded its SBOM.
     */
    private Date started;

}
