package org.kinotic.queue.internal.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.kinotic.queue.api.model.QueueDefinition;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the definition of every queue, shared by all nodes of the cluster. Its methods block.
 */
@Component
public class QueueDefinitionRepository {

    private static final String DEFINITION_CACHE = "kinotic_queue_definitions";

    // Shard count by queue name
    private final IgniteCache<String, Integer> cache;
    // Definitions never change once stored, so a definition read once is kept
    private final ConcurrentHashMap<String, QueueDefinition> known = new ConcurrentHashMap<>();

    public QueueDefinitionRepository(Ignite ignite) {
        CacheConfiguration<String, Integer> configuration = new CacheConfiguration<>(DEFINITION_CACHE);
        configuration.setCacheMode(CacheMode.REPLICATED);
        configuration.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        configuration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        this.cache = ignite.getOrCreateCache(configuration);
    }

    /**
     * Stores the definition unless one with the same name exists.
     *
     * @return the stored definition
     */
    public QueueDefinition saveIfAbsent(QueueDefinition definition) {
        Integer existing = cache.getAndPutIfAbsent(definition.name(), definition.shardCount());
        QueueDefinition ret = existing == null ? definition : new QueueDefinition(definition.name(), existing);
        known.put(ret.name(), ret);
        return ret;
    }

    /**
     * @return the definition, or null when no queue has the name
     */
    public QueueDefinition find(String name) {
        QueueDefinition ret = known.get(name);
        if (ret == null) {
            Integer shardCount = cache.get(name);
            if (shardCount != null) {
                ret = new QueueDefinition(name, shardCount);
                known.put(name, ret);
            }
        }
        return ret;
    }

    /**
     * @return the definition when this node has read it before, without blocking; otherwise null
     */
    public QueueDefinition findKnown(String name) {
        return known.get(name);
    }

    public List<QueueDefinition> findAll() {
        List<QueueDefinition> ret = new ArrayList<>();
        for (Cache.Entry<String, Integer> entry : cache) {
            ret.add(new QueueDefinition(entry.getKey(), entry.getValue()));
        }
        return ret;
    }
}
