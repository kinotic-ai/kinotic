package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.internal.cluster.message.AppendRequest;
import org.kinotic.queue.internal.cluster.message.FetchRequest;
import org.kinotic.queue.internal.cluster.message.FetchResponse;
import org.kinotic.queue.internal.cluster.message.LeaseRequest;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.OffsetCommit;
import org.kinotic.queue.internal.cluster.message.OffsetQuery;
import org.kinotic.queue.internal.cluster.message.ReplicateRequest;
import org.kinotic.queue.internal.cluster.message.SettleRequest;
import org.kinotic.queue.internal.cluster.message.PrepareRequest;
import org.kinotic.queue.internal.cluster.message.OwnerStatus;
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.cluster.message.StatusRequest;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Sends requests to the queue nodes holding a shard's copies, resolving them through {@link ShardPlacement}.
 * Futures complete on the calling context.
 */
@Component
@RequiredArgsConstructor
public class QueueClusterClient {

    private static final long REQUEST_TIMEOUT_MS = 5_000;
    // An owner holds a fetch up to ShardOwner.FETCH_WAIT_MS before answering, so its reply takes longer
    private static final long FETCH_TIMEOUT_MS = REQUEST_TIMEOUT_MS + ShardOwner.FETCH_WAIT_MS;
    // Longer than the owner waits for a majority, so the owner's answer arrives before the request gives up
    private static final long MAJORITY_TIMEOUT_MS = 2 * ShardOwner.COMMIT_TIMEOUT_MS;
    // Well inside the owner's commit timeout, so a lost batch is resent before the appends waiting on it give up
    private static final long REPLICATE_TIMEOUT_MS = ShardOwner.COMMIT_TIMEOUT_MS / 3;
    // A group's first lease on an owner waits for a majority to store the group's position before the owner holds it
    private static final long LEASE_TIMEOUT_MS = MAJORITY_TIMEOUT_MS + ShardOwner.FETCH_WAIT_MS;
    // Long enough for a shard's next owner to be placed and recover after its owner leaves
    private static final long OWNER_DEADLINE_MS = 30_000;
    private static final long RETRY_DELAY_MS = 100;

    private final Vertx vertx;
    private final ShardPlacement placement;
    private AppendBatcher batcher;

    @PostConstruct
    public void start() {
        batcher = new AppendBatcher(vertx, this::sendBatch);
    }

    /**
     * Appends a record through the owner of the shard its key hashes to, in a batch with the other appends this client
     * makes to the shard, retrying while the shard has no active owner.
     *
     * @return the position the record was written at
     */
    public Future<QueuePosition> append(QueueDefinition definition, String key, byte[] payload) {
        int shard = QueueLog.shardOf(key, definition.shardCount());
        return batcher.append(definition.name(), shard, key, payload)
                      .map(offset -> new QueuePosition(definition.name(), shard, offset));
    }

    /**
     * Finds the offset the shard's next record will be committed at, retrying while the shard has no active owner.
     */
    public Future<Long> findCommittedOffset(String queue, int shard) {
        Buffer request = new FetchRequest(queue, shard, -1, 0, 0).toBuffer();
        return retryWhileOwnerless(() -> requestOwner(queue, shard, QueueNode.FETCH, request, REQUEST_TIMEOUT_MS),
                                   System.currentTimeMillis() + OWNER_DEADLINE_MS)
                .map(reply -> FetchResponse.fromBuffer(reply).nextOffset());
    }

    /**
     * Has the shard's owner store the group's position on the shard, starting it at {@code startPosition} when it has
     * none, retrying while the shard has no active owner.
     */
    public Future<Void> joinGroup(String queue, int shard, String groupName, StartPosition startPosition) {
        // A lease of no records only opens the group on the owner
        Buffer request = new LeaseRequest(queue, shard, groupName, "", 0, 1, startPosition).toBuffer();
        return retryWhileOwnerless(() -> requestOwner(queue, shard, QueueNode.LEASE, request, LEASE_TIMEOUT_MS),
                                   System.currentTimeMillis() + OWNER_DEADLINE_MS)
                .mapEmpty();
    }

    /**
     * Fetches committed entries from the shard's owner, which holds the request until an entry at {@code offset}
     * is committed or a short wait passes.
     */
    public Future<FetchResponse> fetch(String queue, int shard, long offset, int max, long maxBytes) {
        return requestOwner(queue, shard, QueueNode.FETCH, new FetchRequest(queue, shard, offset, max, maxBytes).toBuffer(), FETCH_TIMEOUT_MS)
                .map(FetchResponse::fromBuffer);
    }

    /**
     * Asks the shard's owner to lease records to a worker, which it holds until there are records to lease or a
     * short wait passes.
     */
    public Future<LeaseResponse> lease(LeaseRequest request) {
        return requestOwner(request.queue(), request.shard(), QueueNode.LEASE, request.toBuffer(), LEASE_TIMEOUT_MS)
                .map(LeaseResponse::fromBuffer);
    }

    /**
     * Tells the shard's owner what a worker did with a record leased to it.
     */
    public Future<Void> settle(SettleRequest request) {
        return requestOwner(request.queue(), request.shard(), QueueNode.SETTLE, request.toBuffer(), MAJORITY_TIMEOUT_MS)
                .mapEmpty();
    }

    /**
     * Stores a consumer's next offset on a shard on every copy of the shard.
     *
     * @return completes once a majority of the replication factor has stored it
     */
    public Future<Void> commitOffset(String queue, String consumerName, int shard, long nextOffset) {
        return replicas(queue, shard).compose(replicas -> commitOffset(replicas, new OffsetCommit(queue, consumerName, shard, nextOffset)));
    }

    private Future<Void> commitOffset(List<String> replicas, OffsetCommit commit) {
        String queue = commit.queue();
        int shard = commit.shard();
        int quorum = placement.quorum();
        if (replicas.size() < quorum) {
            return Future.failedFuture(QueueFailure.NO_QUORUM.exception("Only " + replicas.size() + " copies of shard " + shard
                                                                                + " of queue " + queue + " are placed, " + quorum + " are needed"));
        }
        Buffer body = commit.toBuffer();
        Promise<Void> ret = Promise.promise();
        int[] succeeded = {0};
        int[] failed = {0};
        for (String replica : replicas) {
            request(replica, QueueNode.COMMIT_OFFSET, body, REQUEST_TIMEOUT_MS).onComplete(ar -> {
                if (ar.succeeded()) {
                    if (++succeeded[0] == quorum) {
                        ret.tryComplete();
                    }
                } else if (++failed[0] > replicas.size() - quorum) {
                    ret.tryFail(ar.cause());
                }
            });
        }
        return ret.future();
    }

    /**
     * @return the consumer's next offset on the shard, the newest any reachable copy stores; zero when none stores one
     */
    public Future<Long> findNextOffset(String queue, String consumerName, int shard) {
        return replicas(queue, shard).compose(replicas -> findNextOffset(replicas, new OffsetQuery(queue, consumerName, shard)));
    }

    private Future<Long> findNextOffset(List<String> replicas, OffsetQuery query) {
        String queue = query.queue();
        int shard = query.shard();
        Buffer body = query.toBuffer();
        List<Future<Long>> replies = replicas.stream()
                                              .map(replica -> request(replica, QueueNode.FIND_OFFSET, body, REQUEST_TIMEOUT_MS)
                                                      .map(OffsetQuery::decodeReply))
                                              .toList();
        return Future.join(replies).transform(ar -> {
            long nextOffset = -1;
            Throwable failure = null;
            for (Future<Long> reply : replies) {
                if (reply.succeeded()) {
                    nextOffset = Math.max(nextOffset, reply.result());
                } else {
                    failure = reply.cause();
                }
            }
            // Delivery restarting from an older offset only repeats records, so any copy that answers will do
            return nextOffset >= 0 ? Future.succeededFuture(nextOffset)
                                   : Future.failedFuture(failure != null ? failure : new IllegalStateException("No copy of shard " + shard + " of queue " + queue + " is placed"));
        });
    }

    public Future<ReplicationResult> replicate(String node, ReplicateRequest request) {
        return request(node, QueueNode.REPLICATE, request.toBuffer(), REPLICATE_TIMEOUT_MS).map(ReplicateRequest::decodeReply);
    }

    /**
     * Reads entries from a node's copy of a shard, committed or not.
     */
    public Future<FetchResponse> read(String node, FetchRequest request) {
        return request(node, QueueNode.READ, request.toBuffer(), REQUEST_TIMEOUT_MS).map(FetchResponse::fromBuffer);
    }

    /**
     * Asks a queue node whether it owns the shard.
     */
    public Future<OwnerStatus> status(String node, String queue, int shard) {
        return request(node, QueueNode.STATUS, new StatusRequest(queue, shard).toBuffer(), REQUEST_TIMEOUT_MS).map(OwnerStatus::fromBuffer);
    }

    /**
     * Passes a request a client sent to this node on to the node serving the shard, with the timeout the client gave it.
     */
    public Future<Buffer> forward(String node, String action, Buffer body) {
        long timeoutMs = switch (action) {
            case QueueNode.APPEND, QueueNode.SETTLE -> MAJORITY_TIMEOUT_MS;
            case QueueNode.FETCH -> FETCH_TIMEOUT_MS;
            case QueueNode.LEASE -> LEASE_TIMEOUT_MS;
            default -> REQUEST_TIMEOUT_MS;
        };
        return request(node, action, body, timeoutMs);
    }

    public Future<ShardStatus> prepare(String node, PrepareRequest request) {
        return request(node, QueueNode.PREPARE, request.toBuffer(), REQUEST_TIMEOUT_MS).map(ShardStatus::fromBuffer);
    }

    private Future<List<Long>> sendBatch(AppendRequest request) {
        Buffer body = request.toBuffer();
        return retryWhileOwnerless(() -> requestOwner(request.queue(), request.shard(), QueueNode.APPEND, body, MAJORITY_TIMEOUT_MS),
                                   System.currentTimeMillis() + OWNER_DEADLINE_MS)
                .map(AppendRequest::decodeReply);
    }

    private Future<Buffer> retryWhileOwnerless(Supplier<Future<Buffer>> request, long deadline) {
        return request.get()
                      .recover(e -> isRetryable(e) && System.currentTimeMillis() < deadline
                              ? vertx.timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS).compose(v -> retryWhileOwnerless(request, deadline))
                              : Future.failedFuture(e));
    }

    // A shard between owners or an owner that left answers with NOT_OWNER, no handler or no reply at all
    private static boolean isRetryable(Throwable e) {
        boolean ret = false;
        if (e instanceof ReplyException reply) {
            ret = reply.failureType() == ReplyFailure.NO_HANDLERS
                    || reply.failureType() == ReplyFailure.TIMEOUT
                    || QueueFailure.fromCode(reply.failureCode()) == QueueFailure.NOT_OWNER;
        }
        return ret;
    }

    private Future<Buffer> requestOwner(String queue, int shard, String action, Buffer body, long timeoutMs) {
        return replicas(queue, shard).compose(replicas -> replicas.isEmpty()
                ? Future.failedFuture(new ReplyException(ReplyFailure.NO_HANDLERS, "No queue node is placed"))
                : request(replicas.getFirst(), action, body, timeoutMs));
    }

    // Placement can block while the cluster changes topology, so it is looked up off the event loop
    private Future<List<String>> replicas(String queue, int shard) {
        Future<List<String>> ret;
        try {
            ret = vertx.executeBlocking(() -> placement.replicas(queue, shard), false);
        } catch (RejectedExecutionException e) {
            // Vert.x throws rather than failing the future once it has closed its worker pool
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    private Future<Buffer> request(String node, String action, Buffer body, long timeoutMs) {
        return vertx.eventBus()
                    .<Buffer>request(QueueNode.address(node, action), body, new DeliveryOptions().setSendTimeout(timeoutMs))
                    .map(Message::body);
    }
}
