package org.kinotic.queue.internal.cluster;

import jakarta.annotation.PreDestroy;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.kinotic.queue.api.model.QueueDefinition;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores the definition of every queue, shared by the cluster's queue nodes, with the incarnation that tells a queue
 * from an earlier one of the same name, and the incarnations of deleted queues. Its methods block.
 */
@Component
public class QueueDefinitionRepository {

    private static final String DEFINITION_CACHE = "kinotic_queue_definitions";
    private static final String DELETED_CACHE = "kinotic_queue_deleted";

    // Each queue by name
    private final IgniteCache<String, QueueRegistration> cache;
    // Queue name by incarnation, for every deleted queue
    private final IgniteCache<String, String> deleted;
    // Counts the changes of the queues this node saw, made on any node
    private final AtomicLong version = new AtomicLong();
    // The queues by name as findKnown returns them, empty for a deleted queue
    private final ConcurrentHashMap<String, Optional<StoredQueue>> known = new ConcurrentHashMap<>();
    private final QueryCursor<Cache.Entry<String, QueueRegistration>> changesSeen;

    public QueueDefinitionRepository(Ignite ignite) {
        this.cache = ignite.getOrCreateCache(ShardPlacement.<QueueRegistration>queueNodeCache(DEFINITION_CACHE));
        this.deleted = ignite.getOrCreateCache(ShardPlacement.<String>queueNodeCache(DELETED_CACHE));
        // Every node learns of a queue's deletion or creation as the cache changes, not only at its next lookup
        ContinuousQuery<String, QueueRegistration> changes = new ContinuousQuery<>();
        changes.setLocalListener(events -> {
            for (CacheEntryEvent<? extends String, ? extends QueueRegistration> event : events) {
                known.put(event.getKey(), event.getEventType() == EventType.REMOVED
                        ? Optional.empty()
                        : Optional.of(event.getValue().toStoredQueue(event.getKey())));
            }
            version.incrementAndGet();
        });
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
        return store(definition, UUID.randomUUID().toString()).toStoredQueue(definition.name());
    }

    /**
     * Stores a queue a node holds a copy of, unless a queue with the same name exists or the incarnation was deleted.
     *
     * @return the incarnation of the queue stored under the name, or null when this incarnation was deleted
     */
    public String saveIfAbsent(QueueDefinition definition, String incarnation) {
        String ret = null;
        if (!deleted.containsKey(incarnation)) {
            QueueRegistration stored = store(definition, incarnation);
            ret = stored.getIncarnation();
            // A deletion recorded between the check and the store takes the queue out again
            if (ret.equals(incarnation) && deleted.containsKey(incarnation)) {
                cache.remove(definition.name(), stored);
                ret = null;
            }
        }
        return ret;
    }

    /**
     * @return the queue as this node last learned of it, without blocking; null when this node knows no queue of the
     * name, which a node still receiving the queues after joining the cluster may lack
     */
    public StoredQueue findKnown(String name) {
        Optional<StoredQueue> ret = known.get(name);
        if (ret == null) {
            // Replicated to every queue node, this one among them, so this node holds every partition, as primary or backup
            QueueRegistration registration = cache.localPeek(name, CachePeekMode.PRIMARY, CachePeekMode.BACKUP);
            // A change the listener recorded wins over the copy, which can apply a change after the listener receives it
            ret = registration != null
                    ? known.computeIfAbsent(name, key -> Optional.of(registration.toStoredQueue(key)))
                    : Optional.empty();
        }
        return ret.orElse(null);
    }

    /**
     * @return the queue with the name as the cluster stores it now, or null when no queue has it
     */
    public StoredQueue findStored(String name) {
        QueueRegistration ret = cache.get(name);
        return ret != null ? ret.toStoredQueue(name) : null;
    }

    /**
     * @return a number that grows whenever this node sees a queue created or deleted
     */
    public long version() {
        return version.get();
    }

    /**
     * As {@link #requireStored}, but without reading the cluster's store when this node's copy of the queues holds the
     * queue of the incarnation.
     */
    public StoredQueue requireKnown(String name, String incarnation) {
        StoredQueue known = findKnown(name);
        return known != null && known.incarnation().equals(incarnation) ? known : requireStored(name, incarnation);
    }

    /**
     * @return the queue with the name as the cluster stores it now
     * @throws QueueFailureException with {@link QueueFailure#NO_QUEUE} when the cluster stores no queue of the name
     *                               and incarnation, which was then deleted
     */
    public StoredQueue requireStored(String name, String incarnation) {
        StoredQueue ret = findStored(name);
        if (ret == null || !ret.incarnation().equals(incarnation)) {
            throw QueueFailure.NO_QUEUE.exception("No queue named " + name + " of incarnation " + incarnation);
        }
        return ret;
    }

    /**
     * @return every queue
     */
    public List<StoredQueue> findAll() {
        List<StoredQueue> ret = new ArrayList<>();
        for (Cache.Entry<String, QueueRegistration> entry : cache) {
            ret.add(entry.getValue().toStoredQueue(entry.getKey()));
        }
        return ret;
    }

    /**
     * Deletes the queue with the name, if there is one, recording its incarnation as deleted.
     */
    public void delete(String name) {
        QueueRegistration value = cache.get(name);
        if (value != null) {
            // Recorded first, so a node that sees the queue gone also sees why
            deleted.put(value.getIncarnation(), name);
            cache.remove(name, value);
        }
    }

    /**
     * @return how many deleted queues the cluster records, without reading them
     */
    public int countDeleted() {
        // Replicated to every queue node, this one among them, so this node holds every partition, as primary or backup
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
        deleted.putAll(deletedQueues);
        // A queue made known again by a node that did not know of its deletion
        deletedQueues.forEach((incarnation, name) -> {
            QueueRegistration value = cache.get(name);
            if (value != null && value.getIncarnation().equals(incarnation)) {
                cache.remove(name, value);
            }
        });
    }

    // The queue stored under the definition's name: this one, or the one stored before it
    private QueueRegistration store(QueueDefinition definition, String incarnation) {
        QueueRegistration value = new QueueRegistration(definition.shardCount(), incarnation);
        QueueRegistration existing = cache.getAndPutIfAbsent(definition.name(), value);
        return existing != null ? existing : value;
    }
}
