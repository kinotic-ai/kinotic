package org.kinotic.queue.internal.cluster;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.cluster.message.FetchRequest;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.cluster.message.LeaseRequest;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.SettleRequest;
import org.kinotic.queue.internal.cluster.message.OffsetCommit;
import org.kinotic.queue.internal.cluster.message.OffsetQuery;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.cluster.message.PrepareRequest;
import org.kinotic.queue.internal.cluster.message.OwnerStatus;
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.cluster.message.StatusRequest;
import org.kinotic.queue.internal.cluster.message.Wire;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ReplicationStatus;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * This node's part of the queue cluster: the copies of the shards placed on it, the owners of the shards it owns,
 * and the event bus endpoints other queue nodes send requests to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueNode {

    static final String APPEND = "append";
    static final String REPLICATE = "replicate";
    static final String FETCH = "fetch";
    static final String READ = "read";
    static final String PREPARE = "prepare";
    static final String COMMIT_OFFSET = "commitOffset";
    static final String FIND_OFFSET = "findOffset";
    static final String LEASE = "lease";
    static final String SETTLE = "settle";
    static final String STATUS = "status";

    private static final long RECONCILE_INTERVAL_MS = 500;
    // How often this node's queue directories are checked against the cluster's queues when no queue was deleted since
    private static final long LOCAL_QUEUES_SYNC_INTERVAL_MS = 60_000;
    private static final long LIFECYCLE_TIMEOUT_SECONDS = 30;
    private static final long METRICS_INTERVAL_MS = 5_000;

    private final Vertx vertx;
    private final KinoticQueueProperties properties;
    private final ShardPlacement placement;
    private final QueueDefinitionRepository definitions;
    private final QueueClusterClient client;
    private final ShardStateRepository shardStates;
    private final QueueMetrics metrics;
    private final ConcurrentHashMap<String, QueueLog> logs = new ConcurrentHashMap<>();
    // Touched only on context
    private final Map<String, ShardOwner> owners = new HashMap<>();
    private final List<MessageConsumer<Buffer>> consumers = new ArrayList<>();
    private Context context;
    private long reconcileTimer;
    private long metricsTimer;
    private boolean reconciling;
    private volatile boolean stopped;
    // The incarnations of deleted queues whose copies this node has deleted; written on worker threads
    private volatile Set<String> deletedQueues = Set.of();
    private volatile long localQueuesSyncedAt;

    /**
     * The event bus address a queue node receives one kind of request on.
     */
    static String address(String nodeId, String action) {
        return "kinotic.queue." + nodeId + "." + action;
    }

    @PostConstruct
    public void start() throws Exception {
        syncLocalQueues();
        CompletableFuture<Void> started = new CompletableFuture<>();
        context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            String self = placement.localNodeId();
            register(self, APPEND, this::onAppend);
            register(self, REPLICATE, this::onReplicate);
            register(self, FETCH, this::onFetch);
            register(self, READ, this::onRead);
            register(self, PREPARE, this::onPrepare);
            register(self, COMMIT_OFFSET, this::onCommitOffset);
            register(self, FIND_OFFSET, this::onFindOffset);
            register(self, LEASE, this::onLease);
            register(self, SETTLE, this::onSettle);
            register(self, STATUS, this::onStatus);
            Future.all(consumers.stream().map(MessageConsumer::completion).toList()).onComplete(ar -> {
                if (ar.succeeded()) {
                    reconcileTimer = vertx.setPeriodic(RECONCILE_INTERVAL_MS, t -> reconcile());
                    metricsTimer = vertx.setPeriodic(METRICS_INTERVAL_MS, t -> refreshMetrics());
                    reconcile();
                    started.complete(null);
                } else {
                    started.completeExceptionally(ar.cause());
                }
            });
        });
        started.get(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        context.runOnContext(v -> {
            stopped = true;
            vertx.cancelTimer(reconcileTimer);
            vertx.cancelTimer(metricsTimer);
            owners.values().forEach(ShardOwner::stop);
            owners.clear();
            Future.join(consumers.stream().map(MessageConsumer::unregister).toList())
                  .onComplete(ar -> done.complete(null));
        });
        done.get(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        logs.values().forEach(QueueLog::close);
        logs.clear();
    }

    /**
     * Opens this node's copy of a queue, creating its directory when the node has none, or holds a copy of an earlier
     * queue of the same name. Blocks on disk access.
     *
     * @throws QueueFailureException with {@link QueueFailure#NO_QUEUE} when the queue was deleted
     */
    public QueueLog localLog(QueueDefinition definition) {
        String incarnation = definitions.findIncarnation(definition.name());
        if (incarnation == null) {
            throw QueueFailure.NO_QUEUE.exception("No queue named " + definition.name());
        }
        QueueLog ret = logs.get(definition.name());
        if (ret == null || !ret.incarnation().equals(incarnation)) {
            // Under the map's lock for the name, so syncLocalQueues never deletes a copy opened meanwhile
            ret = logs.compute(definition.name(), (name, open) -> {
                QueueLog opened = open;
                if (open == null || !open.incarnation().equals(incarnation)) {
                    if (open != null) {
                        open.close();
                    }
                    opened = QueueLog.openOrCreate(queueDirectory(name), definition, incarnation, properties.getQueue());
                }
                return opened;
            });
        }
        return ret;
    }

    private void onAppend(Message<Buffer> message) {
        AppendRequest request = AppendRequest.fromBuffer(message.body());
        serve(message, APPEND, request.queue(), request.shard(), owner -> owner.append(request).map(AppendRequest::encodeReply));
    }

    private void onFetch(Message<Buffer> message) {
        FetchRequest request = FetchRequest.fromBuffer(message.body());
        serve(message, FETCH, request.queue(), request.shard(),
              owner -> owner.fetch(request.offset(), request.max(), request.maxBytes()).map(FetchResponse::toBuffer));
    }

    private void onLease(Message<Buffer> message) {
        LeaseRequest request = LeaseRequest.fromBuffer(message.body());
        if (!QueueLog.isValidName(request.groupName()) || request.max() < 0 || request.leaseMillis() < 1) {
            reply(message, Future.failedFuture(new IllegalArgumentException("Invalid lease of " + request.max() + " records for "
                                                                                     + request.leaseMillis() + " ms to group '"
                                                                                     + request.groupName() + "'")));
        } else {
            serve(message, LEASE, request.queue(), request.shard(), owner -> owner.lease(request).map(LeaseResponse::toBuffer));
        }
    }

    private void onSettle(Message<Buffer> message) {
        SettleRequest request = SettleRequest.fromBuffer(message.body());
        serve(message, SETTLE, request.queue(), request.shard(), owner -> owner.settle(request).map(v -> Wire.buffer()));
    }

    private void onStatus(Message<Buffer> message) {
        StatusRequest request = StatusRequest.fromBuffer(message.body());
        ShardOwner owner = owners.get(request.queue() + "/" + request.shard());
        boolean active = owner != null && owner.isActive();
        reply(message, Future.succeededFuture(new OwnerStatus(active, active ? owner.committedOffset() : -1).toBuffer()));
    }

    // Handles a request for a shard this node owns. While another node still serves the shard as this node's copy
    // catches up, the request goes to that node, so clients reach the shard through the node placed as its owner.
    private void serve(Message<Buffer> message, String action, String queue, int shard, Function<ShardOwner, Future<Buffer>> handler) {
        ShardOwner owner = owners.get(queue + "/" + shard);
        Future<Buffer> result;
        if (owner == null) {
            // A queue created moments ago may not be read here yet, and a deleted one is forgotten at the next reconcile
            result = vertx.executeBlocking(() -> {
                throw definitions.find(queue) == null
                        ? QueueFailure.NO_QUEUE.exception("No queue named " + queue)
                        : QueueFailure.NOT_OWNER.exception("This node does not own shard " + shard + " of queue " + queue);
            }, false);
        } else if (owner.actingOwner() != null) {
            result = client.forward(owner.actingOwner(), action, message.body());
        } else {
            result = handler.apply(owner);
        }
        reply(message, result);
    }

    private void onReplicate(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            ReplicateRequest request = ReplicateRequest.fromBuffer(message.body());
            QueueLog queueLog = localLog(requireDefinition(request.queue()));
            ReplicationResult result = queueLog.shard(request.shard()).replicate(request.batch());
            if (result.status() != ReplicationStatus.STALE_EPOCH) {
                queueLog.consumerOffsets().saveAll(request.shard(), request.consumerOffsets());
                queueLog.groupOffsets().saveAll(request.shard(), request.groupOffsets());
            }
            return ReplicateRequest.encodeReply(result);
        }, false));
    }

    private void onRead(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            FetchRequest request = FetchRequest.fromBuffer(message.body());
            ShardLog shard = findLocalShard(request.queue(), request.shard());
            FetchResponse response;
            if (shard == null || request.offset() >= shard.nextOffset()) {
                response = new FetchResponse(List.of(), request.offset());
            } else {
                List<ShardEntry> entries = shard.read(request.offset(), request.max(), Math.min(request.maxBytes(), ShardOwner.MAX_BATCH_BYTES));
                response = new FetchResponse(entries, request.offset() + entries.size());
            }
            return response.toBuffer();
        }, false));
    }

    private void onPrepare(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            PrepareRequest request = PrepareRequest.fromBuffer(message.body());
            QueueLog queueLog = localLog(requireDefinition(request.queue()));
            long promisedBefore = queueLog.promise(request.shard(), request.epoch());
            ShardLog shard = queueLog.findShard(request.shard());
            ShardStatus status = shard != null ? ShardStatus.of(shard,
                                                                promisedBefore,
                                                                queueLog.consumerOffsets().findAll(request.shard()),
                                                                queueLog.groupOffsets().findAll(request.shard()))
                                               : ShardStatus.none(promisedBefore);
            return status.toBuffer();
        }, false));
    }

    private void onCommitOffset(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            OffsetCommit commit = OffsetCommit.fromBuffer(message.body());
            QueueLog.requireValidName(commit.consumerName());
            localLog(requireDefinition(commit.queue())).consumerOffsets()
                                                       .save(commit.consumerName(), commit.shard(), commit.nextOffset());
            return Wire.buffer();
        }, false));
    }

    private void onFindOffset(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            OffsetQuery query = OffsetQuery.fromBuffer(message.body());
            QueueLog.requireValidName(query.consumerName());
            QueueLog queueLog = findLocalLog(query.queue());
            long nextOffset = queueLog == null ? 0 : queueLog.consumerOffsets().findNextOffset(query.consumerName(), query.shard());
            return OffsetQuery.encodeReply(nextOffset);
        }, false));
    }

    private void reconcile() {
        if (!reconciling) {
            reconciling = true;
            Set<String> acting = new HashSet<>();
            owners.forEach((key, owner) -> {
                if (owner.isActive()) {
                    acting.add(key);
                }
            });
            vertx.executeBlocking(() -> assignedShards(acting), false)
                 .compose(assignments -> {
                     // A reconcile that finishes after stop() would start owners nothing stops
                     if (!stopped) {
                         applyAssignments(assignments);
                     }
                     // Once the deleted queues' owners stopped, their copies go
                     return vertx.executeBlocking(() -> {
                         if (!stopped && localQueuesSyncDue()) {
                             syncLocalQueues();
                         }
                         return null;
                     }, false);
                 })
                 .onComplete(ar -> {
                     reconciling = false;
                     if (ar.failed()) {
                         log.warn("Reconciling queue shard ownership failed", ar.cause());
                     }
                 });
        }
    }

    // The shards placed on this node as their owner, and the shards this node serves while their placed owner catches up
    private Map<String, ShardAssignment> assignedShards(Set<String> acting) {
        String self = placement.localNodeId();
        Map<String, ShardAssignment> ret = new HashMap<>();
        for (QueueDefinition definition : definitions.findAll()) {
            for (int shard = 0; shard < definition.shardCount(); shard++) {
                List<String> replicas = placement.replicas(definition.name(), shard);
                boolean primary = !replicas.isEmpty() && replicas.getFirst().equals(self);
                if (primary || acting.contains(definition.name() + "/" + shard)) {
                    Map<String, String> followers = new LinkedHashMap<>();
                    for (String node : replicas) {
                        if (!node.equals(self)) {
                            String storageId = placement.storageId(node);
                            // A node that left keeps a place no copy answers to
                            followers.put(node, storageId != null ? storageId : "?" + node);
                        }
                    }
                    QueueLog queueLog = localLog(definition);
                    ShardAssignment assignment = new ShardAssignment(queueLog, shard, queueLog.shard(shard), followers, primary);
                    ret.put(assignment.key(), assignment);
                }
            }
        }
        return ret;
    }

    private void applyAssignments(Map<String, ShardAssignment> assignments) {
        // An owner that stepped down for a newer epoch is replaced, so it recovers again if the shard is still placed here
        owners.entrySet().removeIf(entry -> {
            boolean removed = !assignments.containsKey(entry.getKey()) || entry.getValue().isStopped();
            if (removed) {
                entry.getValue().stop();
            }
            return removed;
        });
        for (ShardAssignment assignment : assignments.values()) {
            ShardOwner owner = owners.get(assignment.key());
            if (owner == null && assignment.primary()) {
                String queue = assignment.queue();
                owner = new ShardOwner(vertx, placement, client, shardStates, metrics, assignment,
                                       (groupName, entry) -> deadLetter(queue, groupName, entry));
                owners.put(assignment.key(), owner);
                owner.start(assignment.followers());
            } else if (owner != null) {
                owner.updateFollowers(assignment.followers());
            }
        }
    }

    // Appends a record to the group's dead-letter queue, creating the queue with the source queue's shard count, and a
    // copy of it on this node so it is known again after every queue node restarts
    private Future<Void> deadLetter(String queue, String groupName, ShardEntry entry) {
        return vertx.executeBlocking(() -> {
                        QueueDefinition source = requireDefinition(queue);
                        QueueDefinition stored = definitions.saveIfAbsent(new QueueDefinition(QueueService.deadLetterQueue(queue, groupName),
                                                                                              source.shardCount()));
                        localLog(stored);
                        return stored;
                    }, false)
                    .compose(definition -> client.append(definition.name(), Future.succeededFuture(definition), entry.key(), entry.payload()))
                    .onSuccess(position -> metrics.recordDeadLetter(queue, groupName))
                    .mapEmpty();
    }

    // Reads the owners' progress on the context, then the stored offsets and sizes, which take disk access, off it
    private void refreshMetrics() {
        Map<String, ShardOwner> active = new HashMap<>();
        Map<String, Map<String, Long>> replicaLags = new HashMap<>();
        Map<String, Long> committed = new HashMap<>();
        owners.forEach((key, owner) -> {
            if (owner.isActive()) {
                active.put(key, owner);
                committed.put(key, owner.committedOffset());
                owner.replicaLag().forEach((node, lag) -> replicaLags.computeIfAbsent(owner.queue(), q -> new HashMap<>()).merge(node, lag, Long::sum));
            }
        });
        vertx.executeBlocking(() -> {
            Map<String, Map<String, Long>> consumerLags = new HashMap<>();
            Map<String, Map<String, Long>> groupLags = new HashMap<>();
            Map<String, Long> owned = new HashMap<>();
            active.forEach((key, owner) -> {
                QueueLog queueLog = logs.get(owner.queue());
                if (queueLog != null) {
                    long committedOffset = committed.get(key);
                    owned.merge(owner.queue(), 1L, Long::sum);
                    queueLog.consumerOffsets().findAll(owner.shard()).forEach((consumer, next) -> consumerLags
                            .computeIfAbsent(owner.queue(), q -> new HashMap<>()).merge(consumer, Math.max(0, committedOffset - next), Long::sum));
                    queueLog.groupOffsets().findAll(owner.shard()).forEach((group, watermark) -> groupLags
                            .computeIfAbsent(owner.queue(), q -> new HashMap<>()).merge(group, Math.max(0, committedOffset - watermark), Long::sum));
                }
            });
            Map<String, Long> sizes = new HashMap<>();
            logs.forEach((queue, queueLog) -> sizes.put(queue, queueLog.openShards().values().stream().mapToLong(ShardLog::bytes).sum()));
            metrics.refresh(consumerLags, groupLags, replicaLags, sizes, owned);
            return null;
        }, false).onFailure(e -> log.warn("Refreshing the queue metrics failed", e));
    }

    private boolean localQueuesSyncDue() {
        return !deletedQueues.containsAll(definitions.findDeleted().keySet())
                || System.currentTimeMillis() - localQueuesSyncedAt >= LOCAL_QUEUES_SYNC_INTERVAL_MS;
    }

    // Brings this node's queue directories in line with the cluster's queues. The deleted queues this node and the
    // cluster know of are shared both ways, so a node away during a deletion learns of it, and every node can tell
    // a deleted queue from one the cluster lost track of when every node restarted: a copy of a deleted queue, or of
    // an earlier queue whose name a newer one took, is deleted, and any other copy makes its queue known again.
    private void syncLocalQueues() throws IOException {
        Path dataDirectory = Path.of(properties.getQueue().getDataDirectory());
        Map<String, String> recorded = QueueLog.findDeletedQueues(dataDirectory);
        Map<String, String> deleted = new HashMap<>(definitions.findDeleted());
        deleted.putAll(recorded);
        definitions.saveDeleted(deleted);
        if (!recorded.keySet().containsAll(deleted.keySet())) {
            QueueLog.saveDeletedQueues(dataDirectory, deleted);
        }
        if (Files.isDirectory(dataDirectory)) {
            try (Stream<Path> directories = Files.list(dataDirectory)) {
                for (Path directory : directories.toList()) {
                    QueueDefinition definition = QueueLog.findDefinition(directory);
                    String incarnation = QueueLog.findIncarnation(directory);
                    if (definition != null && incarnation != null) {
                        String current = deleted.containsKey(incarnation) ? null : definitions.saveIfAbsent(definition, incarnation);
                        if (!incarnation.equals(current)) {
                            deleteLocalQueue(definition.name(), incarnation);
                        }
                    }
                }
            }
        }
        deletedQueues = deleted.keySet();
        localQueuesSyncedAt = System.currentTimeMillis();
    }

    private void deleteLocalQueue(String queue, String incarnation) {
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

    private QueueDefinition requireDefinition(String queue) {
        QueueDefinition ret = definitions.find(queue);
        if (ret == null) {
            throw QueueFailure.NO_QUEUE.exception("No queue named " + queue);
        }
        return ret;
    }

    // Opens only queues the definitions name, whose names were validated when the queue was created
    private QueueLog findLocalLog(String queue) {
        QueueLog ret = logs.get(queue);
        if (ret == null) {
            QueueDefinition definition = definitions.find(queue);
            if (definition != null && Files.isDirectory(queueDirectory(definition.name()))) {
                ret = localLog(definition);
            }
        }
        return ret;
    }

    private ShardLog findLocalShard(String queue, int shard) {
        QueueLog queueLog = findLocalLog(queue);
        return queueLog == null ? null : queueLog.findShard(shard);
    }

    private Path queueDirectory(String queue) {
        return Path.of(properties.getQueue().getDataDirectory()).resolve(queue);
    }

    private void register(String nodeId, String action, Handler<Message<Buffer>> handler) {
        consumers.add(vertx.eventBus().consumer(address(nodeId, action), message -> {
            try {
                handler.handle(message);
            } catch (RuntimeException e) {
                // A request this node cannot decode is answered, so its sender does not wait out its timeout
                message.fail(0, String.valueOf(e.getMessage()));
            }
        }));
    }

    private static void reply(Message<Buffer> message, Future<Buffer> result) {
        result.onComplete(ar -> {
            if (ar.succeeded()) {
                message.reply(ar.result());
            } else {
                message.fail(failureCode(ar.cause()), String.valueOf(ar.cause().getMessage()));
            }
        });
    }

    // A forwarded request's failure keeps the code the serving node gave it; a forward that got no answer leaves the
    // client to find the shard's owner again
    private static int failureCode(Throwable cause) {
        int ret = 0;
        if (cause instanceof QueueFailureException failure) {
            ret = failure.getFailure().code();
        } else if (cause instanceof ReplyException reply) {
            ret = reply.failureType() == ReplyFailure.RECIPIENT_FAILURE ? reply.failureCode() : QueueFailure.NOT_OWNER.code();
        }
        return ret;
    }
}
