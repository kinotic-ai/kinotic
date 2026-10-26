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
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.StartPosition;
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
import org.kinotic.queue.internal.log.ReplicationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Sends requests to the queue nodes holding a shard's copies, resolving them through {@link ShardPlacement}.
 * Futures complete on the calling context.
 */
@Component
@RequiredArgsConstructor
public class QueueClusterClient {

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
     * makes to the shard, retrying while the shard has no active owner. Appends to a shard are sent in the order they
     * were called, including appends called while the queue's definition was still being looked up.
     *
     * @param stored the queue as the cluster stores it, or its lookup; the append fails as the lookup does
     * @return the position the record was written at
     */
    public Future<QueuePosition> append(String queue, Future<StoredQueue> stored, String key, byte[] payload) {
        return batcher.append(queue, stored, key, payload);
    }

    /**
     * Finds the offset the shard's next record will be committed at, retrying while the shard has no active owner.
     */
    public Future<Long> findCommittedOffset(ShardId shardId) {
        return requestOwnerRetrying(shardId, QueueAction.COMMITTED_OFFSET, Wire.appendShardId(Wire.buffer(), shardId))
                .map(reply -> new Wire(reply).readLong());
    }

    /**
     * Has the shard's owner store the group's position on the shard, starting it at the latest record when it has
     * none, retrying while the shard has no active owner.
     */
    public Future<Void> joinGroup(ShardId shardId, String groupName) {
        // A lease of no records only opens the group on the owner
        LeaseRequest request = new LeaseRequest(shardId, groupName, "", 0, 1, StartPosition.LATEST);
        return requestOwnerRetrying(shardId, QueueAction.LEASE, request.toBuffer()).mapEmpty();
    }

    /**
     * Fetches committed entries from the shard's owner, which holds the request until an entry at the requested
     * offset is committed or a short wait passes.
     */
    public Future<FetchResponse> fetch(FetchRequest request) {
        return requestOwner(request.shardId(), QueueAction.FETCH, request.toBuffer()).map(FetchResponse::fromBuffer);
    }

    /**
     * Asks the shard's owner to lease records to a worker, which it holds until there are records to lease or a
     * short wait passes.
     */
    public Future<LeaseResponse> lease(LeaseRequest request) {
        return requestOwner(request.shardId(), QueueAction.LEASE, request.toBuffer()).map(LeaseResponse::fromBuffer);
    }

    /**
     * Tells the shard's owner what a worker did with a record leased to it.
     */
    public Future<Void> settle(SettleRequest request) {
        return requestOwner(request.shardId(), QueueAction.SETTLE, request.toBuffer()).mapEmpty();
    }

    /**
     * Stores a consumer's next offset on a shard on every copy of the shard.
     *
     * @return completes once a majority of the replication factor has stored it
     */
    public Future<Void> commitOffset(OffsetCommit commit) {
        return replicas(commit.shardId()).compose(replicas -> commitOffset(replicas, commit));
    }

    private Future<Void> commitOffset(List<String> replicas, OffsetCommit commit) {
        int quorum = placement.quorum();
        if (replicas.size() < quorum) {
            return Future.failedFuture(QueueFailure.NO_QUORUM.exception("Only " + replicas.size() + " copies of " + commit.shardId()
                                                                                + " are placed, " + quorum + " are needed"));
        }
        Buffer body = commit.toBuffer();
        Promise<Void> ret = Promise.promise();
        int[] succeeded = {0};
        int[] failed = {0};
        for (String replica : replicas) {
            request(replica, QueueAction.COMMIT_OFFSET, body).onComplete(ar -> {
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
     * @param startPosition where the consumer starts on the shard when it never committed there; for
     *                      {@link StartPosition#LATEST} a majority of the shard's copies must answer, so a consumer that
     *                      committed is never taken for one that did not
     * @return the consumer's next offset on the shard, the newest the answering copies store; zero when none stores one
     */
    public Future<Long> findNextOffset(OffsetQuery query, StartPosition startPosition) {
        int required = startPosition == StartPosition.LATEST ? placement.quorum() : 1;
        return replicas(query.shardId()).compose(replicas -> findNextOffset(replicas, query, required));
    }

    private Future<Long> findNextOffset(List<String> replicas, OffsetQuery query, int required) {
        Buffer body = query.toBuffer();
        List<Future<Long>> replies = replicas.stream()
                                              .map(replica -> request(replica, QueueAction.FIND_OFFSET, body).map(reply -> new Wire(reply).readLong()))
                                              .toList();
        return Future.join(replies).transform(ar -> {
            long nextOffset = -1;
            int answered = 0;
            Throwable failure = null;
            for (Future<Long> reply : replies) {
                if (reply.succeeded()) {
                    nextOffset = Math.max(nextOffset, reply.result());
                    answered++;
                } else {
                    failure = reply.cause();
                }
            }
            // A commit is stored on a majority, so a majority's answers include it. Fewer can all come from copies that
            // missed it, which only repeats records for a consumer starting at the earliest record.
            return answered >= required
                    ? Future.succeededFuture(nextOffset)
                    : Future.failedFuture(new IllegalStateException("Only " + answered + " copies of " + query.shardId() + " answered, "
                                                                            + required + " are needed", failure));
        });
    }

    public Future<ReplicationResult> replicate(String node, ReplicateRequest request) {
        return request(node, QueueAction.REPLICATE, request.toBuffer()).map(ReplicateRequest::decodeReply);
    }

    /**
     * Reads entries from a node's copy of a shard, committed or not.
     */
    public Future<FetchResponse> read(String node, FetchRequest request) {
        return request(node, QueueAction.READ, request.toBuffer()).map(FetchResponse::fromBuffer);
    }

    /**
     * Asks a queue node whether it owns the shard.
     */
    public Future<OwnerStatus> status(String node, ShardId shardId) {
        return request(node, QueueAction.STATUS, Wire.appendShardId(Wire.buffer(), shardId)).map(OwnerStatus::fromBuffer);
    }

    public Future<ShardStatus> prepare(String node, PrepareRequest request) {
        return request(node, QueueAction.PREPARE, request.toBuffer()).map(ShardStatus::fromBuffer);
    }

    private Future<List<Long>> sendBatch(AppendRequest request) {
        return requestOwnerRetrying(request.shardId(), QueueAction.APPEND, request.toBuffer()).map(AppendRequest::decodeReply);
    }

    private Future<Buffer> requestOwnerRetrying(ShardId shardId, QueueAction action, Buffer body) {
        return retryWhileOwnerless(shardId, action, body, MonotonicClock.millis() + OWNER_DEADLINE_MS);
    }

    private Future<Buffer> retryWhileOwnerless(ShardId shardId, QueueAction action, Buffer body, long deadline) {
        return requestOwner(shardId, action, body)
                .recover(e -> isRetryable(e) && MonotonicClock.millis() < deadline
                        ? vertx.timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS).compose(v -> retryWhileOwnerless(shardId, action, body, deadline))
                        : Future.failedFuture(e));
    }

    // A shard between owners or an owner that left answers with NOT_OWNER, no handler or no reply at all
    private static boolean isRetryable(Throwable e) {
        return e instanceof ReplyException reply
                && (reply.failureType() == ReplyFailure.NO_HANDLERS
                || reply.failureType() == ReplyFailure.TIMEOUT
                || QueueFailure.NOT_OWNER.matches(e));
    }

    private Future<Buffer> requestOwner(ShardId shardId, QueueAction action, Buffer body) {
        return replicas(shardId).compose(replicas -> replicas.isEmpty()
                ? Future.failedFuture(new ReplyException(ReplyFailure.NO_HANDLERS, "No queue node is placed"))
                : request(replicas.getFirst(), action, body));
    }

    // A placement not looked up since the topology changed can block, so it is looked up off the event loop
    private Future<List<String>> replicas(ShardId shardId) {
        List<String> known = placement.findKnownReplicas(shardId.queue(), shardId.shard());
        Future<List<String>> ret;
        if (known != null) {
            ret = Future.succeededFuture(known);
        } else {
            try {
                ret = vertx.executeBlocking(() -> placement.replicas(shardId.queue(), shardId.shard()), false);
            } catch (RejectedExecutionException e) {
                // Vert.x throws rather than failing the future once it has closed its worker pool
                ret = Future.failedFuture(e);
            }
        }
        return ret;
    }

    /**
     * Sends the request to the queue node, answered with the node's reply.
     */
    Future<Buffer> request(String node, QueueAction action, Buffer body) {
        return vertx.eventBus()
                    .<Buffer>request(action.address(node), body, new DeliveryOptions().setSendTimeout(action.getTimeoutMs()))
                    .map(Message::body);
    }
}
