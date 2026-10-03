package org.kinotic.queue.internal.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.util.AttributeNodeFilter;
import org.springframework.stereotype.Component;

/**
 * Stores the highest offset known to be committed on each shard, shared by every queue node while any of them runs.
 * A node about to own a shard compares the copies it can reach against it. Its methods block.
 */
@Component
public class ShardStateRepository {

    private static final String STATE_CACHE = "kinotic_queue_shard_states";

    // Committed offset by "queue/shard"
    private final IgniteCache<String, Long> cache;

    public ShardStateRepository(Ignite ignite) {
        CacheConfiguration<String, Long> configuration = new CacheConfiguration<>(STATE_CACHE);
        configuration.setCacheMode(CacheMode.REPLICATED);
        configuration.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        configuration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        configuration.setNodeFilter(new AttributeNodeFilter(ShardPlacement.QUEUE_NODE_ATTRIBUTE, Boolean.TRUE));
        this.cache = ignite.getOrCreateCache(configuration);
    }

    /**
     * @return the highest offset known to be committed on the shard; zero when none is known
     */
    public long findCommittedOffset(String queue, int shard) {
        Long ret = cache.get(key(queue, shard));
        return ret != null ? ret : 0;
    }

    /**
     * Stores the shard's committed offset unless a higher one is already stored.
     */
    public void saveCommittedOffset(String queue, int shard, long committedOffset) {
        String key = key(queue, shard);
        Long stored = cache.getAndPutIfAbsent(key, committedOffset);
        while (stored != null && stored < committedOffset && !cache.replace(key, stored, committedOffset)) {
            stored = cache.get(key);
        }
    }

    private static String key(String queue, int shard) {
        return queue + "/" + shard;
    }
}
