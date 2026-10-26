package org.kinotic.queue.internal.cluster;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.ShardPositions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Stores this node's copies of the cluster's queues, one directory per queue under the data directory, and keeps them
 * in line with the queues the cluster stores. Its methods block on disk access.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalQueueRepository {

    // How often the copies are checked against the cluster's queues when no queue was deleted since
    private static final long SYNC_INTERVAL_MS = 60_000;

    private final KinoticQueueProperties properties;
    private final QueueDefinitionRepository definitions;
    private final ShardStateRepository shardStates;
    private final QueueMetrics metrics;
    private final ConcurrentHashMap<String, QueueLog> logs = new ConcurrentHashMap<>();
    // The incarnations of deleted queues whose copies this node has deleted
    private volatile Set<String> deletedQueues = Set.of();
    private volatile long syncedAt;
    private volatile long expiredPositionsDeletedAt;

    @PostConstruct
    public void start() throws IOException {
        sync();
    }

    @PreDestroy
    public void close() {
        logs.values().forEach(QueueLog::close);
        logs.clear();
    }

    /**
     * Stores the definition, as a new queue, unless a queue with the same name exists, with a copy on this node, so the
     * queue is known again after every queue node restarts.
     *
     * @return the queue stored under the definition's name
     */
    public StoredQueue saveIfAbsent(QueueDefinition definition) {
        StoredQueue ret = definitions.saveIfAbsent(definition);
        openOrCreate(ret);
        return ret;
    }

    /**
     * Opens this node's copy of a queue, creating it when the node has none, or holds a copy of an earlier queue of the
     * same name.
     *
     * @throws QueueFailureException with {@link QueueFailure#NO_QUEUE} when the queue was deleted
     */
    public QueueLog openOrCreate(StoredQueue queue) {
        QueueLog ret = logs.get(queue.name());
        if (ret == null || !ret.incarnation().equals(queue.incarnation())) {
            // Under the map's lock for the name, so sync never deletes a copy opened meanwhile
            ret = logs.compute(queue.name(), (name, open) -> {
                QueueLog opened = open;
                if (open == null || !open.incarnation().equals(queue.incarnation())) {
                    // Opening a copy deletes a copy of another incarnation, which only a newer queue may do
                    definitions.requireStored(name, queue.incarnation());
                    if (open != null) {
                        open.close();
                    }
                    opened = QueueLog.openOrCreate(queueDirectory(name), queue.definition(), queue.incarnation(), properties.getQueue());
                }
                return opened;
            });
        }
        return ret;
    }

    /**
     * Opens this node's copy of the shard's queue, creating it when the node has none.
     *
     * @throws QueueFailureException with {@link QueueFailure#NO_QUEUE} when the queue was deleted
     */
    public QueueLog openOrCreate(ShardId shardId) {
        return openOrCreate(definitions.requireKnown(shardId.queue(), shardId.incarnation()));
    }

    /**
     * @return this node's copy of the shard's queue, or null when it holds none
     * @throws QueueFailureException with {@link QueueFailure#NO_QUEUE} when the queue was deleted
     */
    public QueueLog findQueue(ShardId shardId) {
        StoredQueue queue = definitions.requireKnown(shardId.queue(), shardId.incarnation());
        QueueLog ret = logs.get(queue.name());
        if (ret != null && !ret.incarnation().equals(queue.incarnation())) {
            ret = null;
        }
        if (ret == null && queue.incarnation().equals(QueueLog.findIncarnation(queueDirectory(queue.name())))) {
            ret = openOrCreate(queue);
        }
        return ret;
    }

    /**
     * @return this node's copy of the shard, or null when it holds none
     * @throws QueueFailureException with {@link QueueFailure#NO_QUEUE} when the queue was deleted
     */
    public ShardLog findShard(ShardId shardId) {
        QueueLog queueLog = findQueue(shardId);
        return queueLog == null ? null : queueLog.findShard(shardId.shard());
    }

    /**
     * @return the positions stored on the shard's open copy that have not expired, or null when no copy of the shard's
     * queue is open
     */
    public ShardPositions findPositions(ShardId shardId) {
        QueueLog queueLog = logs.get(shardId.queue());
        return queueLog != null && queueLog.incarnation().equals(shardId.incarnation()) ? queueLog.positions(shardId.shard()) : null;
    }

    /**
     * @return the bytes the open shards of each open queue copy take on disk, by queue name
     */
    public Map<String, Long> findSizes() {
        Map<String, Long> ret = new HashMap<>();
        logs.forEach((queue, queueLog) -> ret.put(queue, queueLog.bytes()));
        return ret;
    }

    /**
     * Brings this node's copies in line with the cluster's queues once a queue was deleted, or once a while, and
     * deletes the positions that expired.
     */
    public void syncIfDue() throws IOException {
        long now = MonotonicClock.millis();
        if (definitions.countDeleted() != deletedQueues.size() || now - syncedAt >= SYNC_INTERVAL_MS) {
            sync();
        }
        if (now - expiredPositionsDeletedAt >= SYNC_INTERVAL_MS) {
            deleteExpiredPositions();
        }
    }

    // The deleted queues this node and the cluster know of are shared both ways, so a node away during a deletion learns
    // of it, and every node can tell a deleted queue from one the cluster lost track of when every node restarted: a copy
    // of a deleted queue, or of an earlier queue whose name a newer one took, is deleted, and any other copy makes its
    // queue known again.
    private void sync() throws IOException {
        Path dataDirectory = Path.of(properties.getQueue().getDataDirectory());
        Map<String, String> recorded = QueueLog.findDeletedQueues(dataDirectory);
        Map<String, String> clusterDeleted = definitions.findDeleted();
        Map<String, String> deleted = new HashMap<>(clusterDeleted);
        deleted.putAll(recorded);
        if (deleted.size() > clusterDeleted.size()) {
            definitions.saveDeleted(recorded);
        }
        if (deleted.size() > recorded.size()) {
            QueueLog.saveDeletedQueues(dataDirectory, deleted);
        }
        // Owners of a deleted queue stopped before this, so none stores its shards' states again
        Set<String> newlyDeleted = new HashSet<>(deleted.keySet());
        newlyDeleted.removeAll(deletedQueues);
        shardStates.deleteAll(newlyDeleted);
        if (Files.isDirectory(dataDirectory)) {
            try (Stream<Path> directories = Files.list(dataDirectory)) {
                for (Path directory : directories.toList()) {
                    QueueDefinition definition = QueueLog.findDefinition(directory);
                    String incarnation = QueueLog.findIncarnation(directory);
                    if (definition != null && incarnation != null) {
                        String current = deleted.containsKey(incarnation) ? null : storedIncarnation(definition, incarnation);
                        if (!incarnation.equals(current)) {
                            delete(definition.name(), incarnation);
                        }
                    }
                }
            }
        }
        deletedQueues = deleted.keySet();
        syncedAt = MonotonicClock.millis();
    }

    // The incarnation the cluster stores under the copy's name, storing the copy's own when it stores none; read first,
    // since the cluster usually stores the queue already and a store goes to the name's primary node
    private String storedIncarnation(QueueDefinition definition, String incarnation) {
        StoredQueue stored = definitions.findStored(definition.name());
        return stored != null ? stored.incarnation() : definitions.saveIfAbsent(definition, incarnation);
    }

    // Consumers and groups that stopped using a queue leave positions, which expire unused
    private void deleteExpiredPositions() {
        for (QueueLog queueLog : logs.values()) {
            try {
                queueLog.deleteExpiredPositions();
            } catch (RuntimeException e) {
                log.warn("Deleting the expired positions of queue {} failed", queueLog.definition().name(), e);
            }
        }
        expiredPositionsDeletedAt = MonotonicClock.millis();
    }

    private void delete(String queue, String incarnation) {
        // Under the map's lock for the name, so a copy of a newer queue of the name opened meanwhile stays
        logs.compute(queue, (name, open) -> {
            QueueLog ret = open;
            if (incarnation.equals(QueueLog.findIncarnation(queueDirectory(name)))) {
                if (open != null) {
                    open.close();
                }
                QueueLog.delete(queueDirectory(name));
                metrics.removeQueue(name);
                log.info("Deleted this node's copy of queue {}, which was deleted", name);
                ret = null;
            }
            return ret;
        });
    }

    private Path queueDirectory(String queue) {
        return Path.of(properties.getQueue().getDataDirectory()).resolve(queue);
    }
}
