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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the definition of every queue, shared by all nodes of the cluster, with the incarnation that tells a queue
 * from an earlier one of the same name, and the incarnations of deleted queues. Its methods block.
 */
@Component
public class QueueDefinitionRepository {

    private static final String DEFINITION_CACHE = "kinotic_queue_definitions";
    private static final String DELETED_CACHE = "kinotic_queue_deleted";

    // "<shard count> <incarnation>" by queue name
    private final IgniteCache<String, String> cache;
    // Queue name by incarnation, for every deleted queue
    private final IgniteCache<String, String> deleted;
    // The value last read for each queue, so a lookup on an event loop never blocks
    private final ConcurrentHashMap<String, String> known = new ConcurrentHashMap<>();

    public QueueDefinitionRepository(Ignite ignite) {
        this.cache = ignite.getOrCreateCache(replicated(DEFINITION_CACHE));
        this.deleted = ignite.getOrCreateCache(replicated(DELETED_CACHE));
    }

    /**
     * Stores the definition, as a new incarnation, unless a queue with the same name exists.
     *
     * @return the stored definition
     */
    public QueueDefinition saveIfAbsent(QueueDefinition definition) {
        return toDefinition(definition.name(), store(definition, UUID.randomUUID().toString()));
    }

    /**
     * Stores a queue a node holds a copy of, unless a queue with the same name exists or the incarnation was deleted.
     *
     * @return the incarnation of the queue stored under the name, or null when this incarnation was deleted
     */
    public String saveIfAbsent(QueueDefinition definition, String incarnation) {
        String stored = deleted.containsKey(incarnation) ? null : store(definition, incarnation);
        return stored != null ? incarnation(stored) : null;
    }

    /**
     * @return the definition, or null when no queue has the name
     */
    public QueueDefinition find(String name) {
        String ret = known.get(name);
        if (ret == null) {
            ret = cache.get(name);
            if (ret != null) {
                known.put(name, ret);
            }
        }
        return ret != null ? toDefinition(name, ret) : null;
    }

    /**
     * @return the definition when this node read it lately, without blocking; otherwise null. A queue deleted since is
     * still found until the next {@link #findAll()}.
     */
    public QueueDefinition findKnown(String name) {
        String ret = known.get(name);
        return ret != null ? toDefinition(name, ret) : null;
    }

    /**
     * @return the incarnation of the queue with the name, or null when no queue has it
     */
    public String findIncarnation(String name) {
        String ret = known.get(name);
        if (ret == null) {
            ret = cache.get(name);
        }
        return ret != null ? incarnation(ret) : null;
    }

    /**
     * @return every queue's definition; the definitions {@link #findKnown} returns become these
     */
    public List<QueueDefinition> findAll() {
        List<QueueDefinition> ret = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Cache.Entry<String, String> entry : cache) {
            ret.add(toDefinition(entry.getKey(), entry.getValue()));
            names.add(entry.getKey());
            known.put(entry.getKey(), entry.getValue());
        }
        known.keySet().retainAll(names);
        return ret;
    }

    /**
     * Deletes the queue, recording its incarnation as deleted.
     *
     * @return whether a queue had the name
     */
    public boolean delete(String name) {
        String value = cache.get(name);
        if (value != null) {
            // Recorded first, so a node that sees the queue gone also sees why
            deleted.put(incarnation(value), name);
            cache.remove(name, value);
            known.remove(name);
        }
        return value != null;
    }

    /**
     * @return the name of every deleted queue, by its incarnation
     */
    public Map<String, String> findDeleted() {
        Map<String, String> ret = new HashMap<>();
        for (Cache.Entry<String, String> entry : deleted) {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    /**
     * Records the incarnations as deleted, and removes the queues stored under them.
     *
     * @param deletedQueues queue names by incarnation
     */
    public void saveDeleted(Map<String, String> deletedQueues) {
        deleted.putAll(deletedQueues);
        deletedQueues.forEach((incarnation, name) -> {
            String value = cache.get(name);
            if (value != null && incarnation(value).equals(incarnation)) {
                cache.remove(name, value);
                known.remove(name);
            }
        });
    }

    // The value stored under the definition's name: this one, or the one stored before it
    private String store(QueueDefinition definition, String incarnation) {
        String value = definition.shardCount() + " " + incarnation;
        String existing = cache.getAndPutIfAbsent(definition.name(), value);
        String ret = existing != null ? existing : value;
        known.put(definition.name(), ret);
        return ret;
    }

    private static CacheConfiguration<String, String> replicated(String name) {
        CacheConfiguration<String, String> ret = new CacheConfiguration<>(name);
        ret.setCacheMode(CacheMode.REPLICATED);
        ret.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        ret.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        return ret;
    }

    private static QueueDefinition toDefinition(String name, String value) {
        return new QueueDefinition(name, Integer.parseInt(value.substring(0, value.indexOf(' '))));
    }

    private static String incarnation(String value) {
        return value.substring(value.indexOf(' ') + 1);
    }
}
