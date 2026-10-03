package org.kinotic.queue.api.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Queue storage and replication settings.
 */
@Getter
@Setter
@Accessors(chain = true)
public class QueueProperties {

    /**
     * The local directory this node keeps its queue shards and consumer offsets in. It must be on a local disk:
     * the shards are memory-mapped files, which network file systems do not support.
     */
    @NotBlank
    private String dataDirectory;

    /**
     * How many queue nodes hold a copy of every shard. A write completes once a majority of these copies has it,
     * so a shard stays writable while a majority of its copies is reachable. Must be the same on every queue node.
     */
    @Min(1)
    private int replicationFactor = 3;

}
