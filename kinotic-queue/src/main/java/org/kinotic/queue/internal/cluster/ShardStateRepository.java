package org.kinotic.queue.internal.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores the highest offset known to be committed on each shard of each queue incarnation, shared by every queue node
 * while any of them runs. A node about to own a shard compares the copies it can reach against it. Its methods block.
 */
@Component
public class ShardStateRepository {

    private static final String STATE_CACHE = "kinotic_queue_shard_states";

    // Committed offset by "incarnation/shard"
    private final IgniteCache<String, Long> cache;

    public ShardStateRepository(Ignite ignite) {
        this.cache = ignite.getOrCreateCache(ShardPlacement.<Long>queueNodeCache(STATE_CACHE));
    }

    /**
     * @return the highest offset known to be committed on the shard; zero when none is known
     */
    public long findCommittedOffset(ShardId shardId) {
        Long ret = cache.get(key(shardId));
        return ret != null ? ret : 0;
    }

    /**
     * Stores the shard's committed offset unless a higher one is already stored.
     */
    public void saveCommittedOffset(ShardId shardId, long committedOffset) {
        String key = key(shardId);
        Long stored = cache.getAndPutIfAbsent(key, committedOffset);
        while (stored != null && stored < committedOffset && !cache.replace(key, stored, committedOffset)) {
            stored = cache.get(key);
        }
    }

    /**
     * Deletes what is stored for the shards of the deleted queues with the incarnations.
     */
    public void deleteAll(Set<String> incarnations) {
        if (!incarnations.isEmpty()) {
            Set<String> keys = new HashSet<>();
            for (Cache.Entry<String, Long> entry : cache) {
                if (incarnations.contains(entry.getKey().substring(0, entry.getKey().lastIndexOf('/')))) {
                    keys.add(entry.getKey());
                }
            }
            cache.removeAll(keys);
        }
    }

    private static String key(ShardId shardId) {
        return shardId.incarnation() + "/" + shardId.shard();
    }
}
