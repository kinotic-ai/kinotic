package org.kinotic.queue.internal.cluster;

import jakarta.annotation.PreDestroy;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.configuration.CacheConfiguration;
import org.kinotic.queue.api.model.QueueDefinition;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.event.EventType;
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
    // The queues as this node last saw them, kept current by changesSeen, so a lookup on an event loop never blocks
    private final ConcurrentHashMap<String, String> known = new ConcurrentHashMap<>();
    private final QueryCursor<Cache.Entry<String, String>> changesSeen;

    public QueueDefinitionRepository(Ignite ignite) {
        this.cache = ignite.getOrCreateCache(replicated(DEFINITION_CACHE));
        this.deleted = ignite.getOrCreateCache(replicated(DELETED_CACHE));
        // Every node learns of a queue's deletion or creation as the cache changes, not only at its next lookup
        ContinuousQuery<String, String> changes = new ContinuousQuery<>();
        changes.setLocalListener(events -> events.forEach(event -> {
            if (event.getEventType() == EventType.REMOVED || event.getEventType() == EventType.EXPIRED || event.getValue() == null) {
                known.remove(event.getKey());
            } else {
                known.put(event.getKey(), event.getValue());
            }
        }));
        this.changesSeen = cache.query(changes);
    }

    @PreDestroy
    public void close() {
        changesSeen.close();
    }

    /**
     * Stores the definition, as a new incarnation, unless a queue with the same name exists.
     *
     * @return the stored queue
     */
    public StoredQueue saveIfAbsent(QueueDefinition definition) {
        return toStored(definition.name(), store(definition, UUID.randomUUID().toString()));
    }

    /**
     * Stores a queue a node holds a copy of, unless a queue with the same name exists or the incarnation was deleted.
     *
     * @return the incarnation of the queue stored under the name, or null when this incarnation was deleted
     */
    public String saveIfAbsent(QueueDefinition definition, String incarnation) {
        String ret = null;
        if (!deleted.containsKey(incarnation)) {
            String stored = store(definition, incarnation);
            ret = incarnation(stored);
            // A deletion recorded between the check and the store takes the queue out again
            if (ret.equals(incarnation) && deleted.containsKey(incarnation)) {
                cache.remove(definition.name(), stored);
                known.remove(definition.name());
                ret = null;
            }
        }
        return ret;
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
     * @return the queue as this node last saw it, without blocking; null when this node has not seen it or saw it
     * deleted. A change made on another node is seen once the cache notifies this node of it.
     */
    public StoredQueue findKnown(String name) {
        String ret = known.get(name);
        return ret != null ? toStored(name, ret) : null;
    }

    /**
     * @return the queue with the name as the cluster stores it now, or null when no queue has it
     */
    public StoredQueue findStored(String name) {
        String ret = cache.get(name);
        if (ret != null) {
            known.put(name, ret);
        } else {
            known.remove(name);
        }
        return ret != null ? toStored(name, ret) : null;
    }

    /**
     * @return every queue; {@link #findKnown} then finds each, and no longer finds others
     */
    public List<StoredQueue> findAll() {
        List<StoredQueue> ret = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Cache.Entry<String, String> entry : cache) {
            ret.add(toStored(entry.getKey(), entry.getValue()));
            names.add(entry.getKey());
            // Never over a value the change listener set meanwhile, which may be newer
            known.putIfAbsent(entry.getKey(), entry.getValue());
        }
        known.keySet().retainAll(names);
        return ret;
    }

    /**
     * Deletes the queue, recording its incarnation as deleted.
     *
     * @return the queue deleted, or null when no queue had the name
     */
    public StoredQueue delete(String name) {
        String value = cache.get(name);
        if (value != null) {
            // Recorded first, so a node that sees the queue gone also sees why
            deleted.put(incarnation(value), name);
            cache.remove(name, value);
            known.remove(name);
        }
        return value != null ? toStored(name, value) : null;
    }

    /**
     * @return how many deleted queues the cluster records, without reading them
     */
    public int countDeleted() {
        // Replicated, so this node holds every partition, as primary or backup
        return deleted.localSize(CachePeekMode.PRIMARY, CachePeekMode.BACKUP);
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
        Map<String, String> unrecorded = new HashMap<>(deletedQueues);
        unrecorded.keySet().removeAll(findDeleted().keySet());
        deleted.putAll(unrecorded);
        // A queue made known again by a node that did not know of its deletion
        unrecorded.forEach((incarnation, name) -> {
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

    private static StoredQueue toStored(String name, String value) {
        return new StoredQueue(toDefinition(name, value), incarnation(value));
    }

    private static QueueDefinition toDefinition(String name, String value) {
        return new QueueDefinition(name, Integer.parseInt(value.substring(0, value.indexOf(' '))));
    }

    private static String incarnation(String value) {
        return value.substring(value.indexOf(' ') + 1);
    }
}
