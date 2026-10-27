package org.kinotic.queue.internal.cluster;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.cluster.message.FetchRequest;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.cluster.message.LeaseRequest;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.OffsetCommit;
import org.kinotic.queue.internal.cluster.message.OffsetQuery;
import org.kinotic.queue.internal.cluster.message.OwnerStatus;
import org.kinotic.queue.internal.cluster.message.PrepareRequest;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.cluster.message.SettleRequest;
import org.kinotic.queue.internal.cluster.message.ShardId;
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.cluster.message.Wire;
import org.kinotic.queue.internal.log.ConsumerPosition;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ReplicationStatus;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.ShardPositions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * This node's part of the queue cluster: the owners of the shards it owns, and the event bus endpoints other queue
 * nodes send requests to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueNode {

    private static final long RECONCILE_INTERVAL_MS = 500;
    // How often the shards placed on this node are computed again when nothing they depend on is seen to change
    private static final long FULL_RECONCILE_INTERVAL_MS = 10_000;
    private static final long LIFECYCLE_TIMEOUT_SECONDS = 30;
    private static final long METRICS_INTERVAL_MS = 5_000;

    private final Vertx vertx;
    private final ShardPlacement placement;
    private final QueueDefinitionRepository definitions;
    private final LocalQueueRepository localQueues;
    private final QueueClusterClient client;
    private final ShardStateRepository shardStates;
    private final QueueMetrics metrics;
    // Touched only on context
    private final Map<ShardId, ShardOwner> owners = new HashMap<>();
    private final List<MessageConsumer<Buffer>> consumers = new ArrayList<>();
    private Context context;
    private long reconcileTimer;
    private long metricsTimer;
    private boolean reconciling;
    // What the last applied assignment was computed from: the placement and the queues
    private long reconciledPlacement = -1;
    private long reconciledDefinitions = -1;
    private long reconciledAt;
    private volatile boolean stopped;

    @PostConstruct
    public void start() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            register(QueueAction.APPEND, this::onAppend);
            register(QueueAction.REPLICATE, this::onReplicate);
            register(QueueAction.FETCH, this::onFetch);
            register(QueueAction.COMMITTED_OFFSET, this::onCommittedOffset);
            register(QueueAction.READ, this::onRead);
            register(QueueAction.PREPARE, this::onPrepare);
            register(QueueAction.COMMIT_OFFSET, this::onCommitOffset);
            register(QueueAction.FIND_OFFSET, this::onFindOffset);
            register(QueueAction.LEASE, this::onLease);
            register(QueueAction.SETTLE, this::onSettle);
            register(QueueAction.STATUS, this::onStatus);
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
    }

    private void onAppend(Message<Buffer> message) {
        AppendRequest request = AppendRequest.fromBuffer(message.body());
        serve(message, QueueAction.APPEND, request.shardId(), owner -> owner.append(request).map(AppendRequest::encodeReply));
    }

    private void onFetch(Message<Buffer> message) {
        FetchRequest request = FetchRequest.fromBuffer(message.body());
        serve(message, QueueAction.FETCH, request.shardId(),
              owner -> owner.fetch(request.offset(), request.max(), request.maxBytes()).map(FetchResponse::toBuffer));
    }

    private void onCommittedOffset(Message<Buffer> message) {
        ShardId shardId = new Wire(message.body()).readShardId();
        serve(message, QueueAction.COMMITTED_OFFSET, shardId,
              owner -> owner.findCommittedOffset().map(offset -> Wire.buffer().appendLong(offset)));
    }

    private void onLease(Message<Buffer> message) {
        LeaseRequest request = LeaseRequest.fromBuffer(message.body());
        if (!QueueLog.isValidName(request.groupName()) || request.max() < 0 || request.leaseMillis() < 1) {
            reply(message, Future.failedFuture(new IllegalArgumentException("Invalid lease of " + request.max() + " records for "
                                                                                     + request.leaseMillis() + " ms to group '"
                                                                                     + request.groupName() + "'")));
        } else {
            serve(message, QueueAction.LEASE, request.shardId(), owner -> owner.lease(request).map(LeaseResponse::toBuffer));
        }
    }

    private void onSettle(Message<Buffer> message) {
        SettleRequest request = SettleRequest.fromBuffer(message.body());
        serve(message, QueueAction.SETTLE, request.shardId(), owner -> owner.settle(request).map(v -> Wire.buffer()));
    }

    private void onStatus(Message<Buffer> message) {
        ShardOwner owner = owners.get(new Wire(message.body()).readShardId());
        boolean active = owner != null && owner.active();
        reply(message, Future.succeededFuture(new OwnerStatus(active, active ? owner.committedOffset() : -1).toBuffer()));
    }

    // Handles a request for a shard this node owns. While another node still serves the shard as this node's copy
    // catches up, the request goes to that node, so clients reach the shard through the node placed as its owner.
    private void serve(Message<Buffer> message, QueueAction action, ShardId shardId, Function<ShardOwner, Future<Buffer>> handler) {
        ShardOwner owner = owners.get(shardId);
        Future<Buffer> result;
        if (owner == null) {
            // A request for a deleted queue fails for good; an owner of the queue may not have started here yet, or be
            // replaced at the next reconcile when it owns an earlier queue of the name
            result = vertx.executeBlocking(() -> {
                definitions.requireStored(shardId.queue(), shardId.incarnation());
                throw QueueFailure.NOT_OWNER.exception("This node does not own " + shardId);
            }, false);
        } else if (owner.actingOwner() != null) {
            // Passed on to the node serving the shard while this node's copy catches up
            result = client.request(owner.actingOwner(), action, message.body());
        } else {
            result = handler.apply(owner);
        }
        reply(message, result);
    }

    private void onReplicate(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            ReplicateRequest request = ReplicateRequest.fromBuffer(message.body());
            int shard = request.shardId().shard();
            QueueLog queueLog = localQueues.openOrCreate(request.shardId());
            ReplicationResult result = queueLog.shard(shard).replicate(request.batch());
            if (result.status() != ReplicationStatus.STALE_EPOCH) {
                queueLog.savePositions(shard, request.positions());
            }
            return ReplicateRequest.encodeReply(result);
        }, false));
    }

    private void onRead(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            FetchRequest request = FetchRequest.fromBuffer(message.body());
            ShardLog shard = localQueues.findShard(request.shardId());
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
            return ShardStatus.prepare(localQueues.openOrCreate(request.shardId()), request.shardId().shard(), request.epoch()).toBuffer();
        }, false));
    }

    private void onCommitOffset(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            OffsetCommit commit = OffsetCommit.fromBuffer(message.body());
            QueueLog.requireValidName(commit.consumerName());
            localQueues.openOrCreate(commit.shardId()).consumerOffsets().save(commit.consumerName(), commit.shardId().shard(), commit.nextOffset());
            return Wire.buffer();
        }, false));
    }

    private void onFindOffset(Message<Buffer> message) {
        reply(message, vertx.executeBlocking(() -> {
            OffsetQuery query = OffsetQuery.fromBuffer(message.body());
            QueueLog.requireValidName(query.consumerName());
            QueueLog queueLog = localQueues.findQueue(query.shardId());
            long nextOffset = queueLog == null ? 0 : queueLog.consumerOffsets().findNextOffset(query.consumerName(), query.shardId().shard());
            return Wire.buffer().appendLong(nextOffset);
        }, false));
    }

    // Computes the shards placed on this node again when the placement or the queues changed, an owner stopped, or a
    // while passed
    private void reconcile() {
        if (!reconciling) {
            reconciling = true;
            Set<ShardId> acting = new HashSet<>();
            owners.forEach((shardId, owner) -> {
                if (owner.active()) {
                    acting.add(shardId);
                }
            });
            long placementVersion = placement.version();
            long definitionsVersion = definitions.version();
            long now = MonotonicClock.millis();
            boolean due = placementVersion != reconciledPlacement
                    || definitionsVersion != reconciledDefinitions
                    || owners.values().stream().anyMatch(ShardOwner::stopped)
                    || now - reconciledAt >= FULL_RECONCILE_INTERVAL_MS;
            Future<Void> assigned = Future.succeededFuture();
            if (due) {
                assigned = vertx.executeBlocking(() -> assignedShards(acting), false).map(assignments -> {
                    // A reconcile that finishes after stop() would start owners nothing stops
                    if (!stopped) {
                        applyAssignments(assignments);
                        reconciledPlacement = placementVersion;
                        reconciledDefinitions = definitionsVersion;
                        reconciledAt = now;
                    }
                    return null;
                });
            }
            // Once the deleted queues' owners stopped, their copies go
            assigned.compose(v -> vertx.executeBlocking(() -> {
                     if (!stopped) {
                         localQueues.syncIfDue();
                     }
                     return null;
                 }, false))
                 .onComplete(ar -> {
                     reconciling = false;
                     if (ar.failed()) {
                         log.warn("Reconciling queue shard ownership failed", ar.cause());
                     }
                 });
        }
    }

    // The shards placed on this node as their owner, and the shards this node serves while their placed owner catches up
    private Map<ShardId, ShardAssignment> assignedShards(Set<ShardId> acting) {
        String self = placement.localNodeId();
        Map<ShardId, ShardAssignment> ret = new HashMap<>();
        for (StoredQueue queue : definitions.findAll()) {
            for (int shard = 0; shard < queue.definition().shardCount(); shard++) {
                ShardId shardId = queue.shardId(shard);
                List<String> replicas = placement.replicas(queue.name(), shard);
                boolean primary = !replicas.isEmpty() && replicas.getFirst().equals(self);
                if (primary || acting.contains(shardId)) {
                    QueueLog queueLog = localQueues.openOrCreate(queue);
                    ret.put(shardId, new ShardAssignment(shardId, queueLog, queueLog.shard(shard), placement.followers(replicas), primary));
                }
            }
        }
        return ret;
    }

    private void applyAssignments(Map<ShardId, ShardAssignment> assignments) {
        // An owner that stepped down for a newer epoch is replaced, so it recovers again if the shard is still placed here,
        // and so is the owner of a deleted queue whose name a newer queue took
        owners.entrySet().removeIf(entry -> {
            boolean removed = !assignments.containsKey(entry.getKey()) || entry.getValue().stopped();
            if (removed) {
                entry.getValue().stop();
            }
            return removed;
        });
        for (ShardAssignment assignment : assignments.values()) {
            ShardOwner owner = owners.get(assignment.shardId());
            if (owner == null && assignment.primary()) {
                String queue = assignment.shardId().queue();
                owner = new ShardOwner(vertx, placement, client, shardStates, metrics, assignment,
                                       (groupName, entry) -> deadLetter(queue, groupName, entry));
                owners.put(assignment.shardId(), owner);
                owner.start();
            } else if (owner != null) {
                owner.updateFollowers(assignment.followers());
            }
        }
    }

    // Appends a record to the group's dead-letter queue, creating the queue with the source queue's shard count and
    // syncWrites when this node does not know it yet
    private Future<Void> deadLetter(String queue, String groupName, ShardEntry entry) {
        String deadLetterQueue = QueueService.deadLetterQueue(queue, groupName);
        StoredQueue known = definitions.findKnown(deadLetterQueue);
        Future<StoredQueue> stored = known != null ? Future.succeededFuture(known) : vertx.executeBlocking(() -> {
            StoredQueue source = definitions.findStored(queue);
            if (source == null) {
                throw QueueFailure.NO_QUEUE.exception("No queue named " + queue);
            }
            QueueDefinition definition = source.definition();
            return localQueues.saveIfAbsent(new QueueDefinition(deadLetterQueue, definition.shardCount(), definition.syncWrites()));
        }, false);
        return client.append(deadLetterQueue, stored, entry.key(), entry.payload())
                     .onSuccess(position -> metrics.recordDeadLetter(queue, groupName))
                     .mapEmpty();
    }

    // Reads the owners' progress on the context, then the stored positions and sizes, which take disk access, off it
    private void refreshMetrics() {
        Map<ShardId, Long> committed = new HashMap<>();
        Map<String, Map<String, Long>> replicaLags = new HashMap<>();
        owners.forEach((shardId, owner) -> {
            if (owner.active()) {
                committed.put(shardId, owner.committedOffset());
                owner.replicaLag().forEach((node, lag) -> replicaLags.computeIfAbsent(shardId.queue(), q -> new HashMap<>()).merge(node, lag, Long::sum));
            }
        });
        vertx.executeBlocking(() -> {
            // A refresh queued as the node stopped would register gauges no one removes again
            if (stopped) {
                return null;
            }
            Map<String, Map<String, Long>> consumerLags = new HashMap<>();
            Map<String, Map<String, Long>> groupLags = new HashMap<>();
            Map<String, Long> owned = new HashMap<>();
            committed.forEach((shardId, committedOffset) -> {
                ShardPositions positions = localQueues.findPositions(shardId);
                if (positions != null) {
                    owned.merge(shardId.queue(), 1L, Long::sum);
                    addLags(consumerLags.computeIfAbsent(shardId.queue(), q -> new HashMap<>()), positions.consumers(), committedOffset);
                    addLags(groupLags.computeIfAbsent(shardId.queue(), q -> new HashMap<>()), positions.groups(), committedOffset);
                }
            });
            metrics.refresh(consumerLags, groupLags, replicaLags, localQueues.findSizes(), owned);
            return null;
        }, false).onFailure(e -> log.warn("Refreshing the queue metrics failed", e));
    }

    private static void addLags(Map<String, Long> lags, Map<String, ConsumerPosition> positions, long committedOffset) {
        positions.forEach((name, position) -> lags.merge(name, Math.max(0, committedOffset - position.nextOffset()), Long::sum));
    }

    private void register(QueueAction action, Handler<Message<Buffer>> handler) {
        consumers.add(vertx.eventBus().consumer(action.address(placement.localNodeId()), message -> {
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
                message.fail(QueueFailure.codeOf(ar.cause()), String.valueOf(ar.cause().getMessage()));
            }
        });
    }
}
