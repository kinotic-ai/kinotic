package org.kinotic.queue.api.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

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
     * so a shard stays writable while a majority of its copies is reachable. A copy keeps its vote while its node is
     * away, and hands it to a new copy only once the new copy has caught up, so a shard whose voting copies lose their
     * majority for good, their nodes gone with their disks, stays unavailable rather than serving fewer records than it
     * acknowledged. Must be the same on every queue node.
     */
    @Min(1)
    private int replicationFactor = 3;

    /**
     * Whether every write is forced to disk before it counts toward a majority. With it, an acknowledged record
     * survives every copy losing power at once; without it, a record survives as long as a majority of its copies
     * keeps running or shuts down cleanly, and writes are faster.
     */
    private boolean syncWrites = true;

    /**
     * How long records are kept. Records are deleted in whole segments: a segment closes at the first record appended
     * once it is half this old, a day old, or a quarter of {@link #retentionBytes} large, and is deleted once the
     * segment after it is this old, so a record lives at least this long. Consumers and worker groups that have not
     * reached a deleted record continue at the oldest record kept. Must be the same on every queue node.
     */
    @NotNull
    @DurationMin(seconds = 1)
    private Duration retentionPeriod = Duration.ofDays(7);

    /**
     * The size each shard is kept under, or null to limit shards by {@link #retentionPeriod} only. The oldest segments
     * of a larger shard are deleted, so a shard's records take about this much space on each copy, plus the segment
     * still taking records: up to a quarter of this, but at least 64 KB and at most 1 GB. Must be the same on every
     * queue node.
     */
    private DataSize retentionBytes;

}
