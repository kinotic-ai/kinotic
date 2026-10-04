package org.kinotic.queue.internal.cluster;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import lombok.RequiredArgsConstructor;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
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
import org.kinotic.queue.internal.cluster.message.ShardStatus;
import org.kinotic.queue.internal.log.QueueLog;
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

    private static final long REQUEST_TIMEOUT_MS = 5_000;
    // An owner holds a fetch up to ShardOwner.FETCH_WAIT_MS before answering, so its reply takes longer
    private static final long FETCH_TIMEOUT_MS = REQUEST_TIMEOUT_MS + ShardOwner.FETCH_WAIT_MS;
    // Longer than the owner waits for a majority, so the owner's answer arrives before the request gives up
    private static final long APPEND_TIMEOUT_MS = 2 * ShardOwner.COMMIT_TIMEOUT_MS;
    // Well inside the owner's commit timeout, so a lost batch is resent before the appends waiting on it give up
    private static final long REPLICATE_TIMEOUT_MS = ShardOwner.COMMIT_TIMEOUT_MS / 3;
    // Long enough for a shard's next owner to be placed and recover after its owner leaves
    private static final long APPEND_DEADLINE_MS = 30_000;
    private static final long RETRY_DELAY_MS = 100;

    private final Vertx vertx;
    private final ShardPlacement placement;

    /**
     * Appends a record through the owner of the shard its key hashes to, retrying while the shard has no active owner.
     *
     * @return the position the record was written at
     */
    public Future<QueuePosition> append(QueueDefinition definition, String key, byte[] payload) {
        int shard = QueueLog.shardOf(key, definition.shardCount());
        AppendRequest request = new AppendRequest(definition.name(), shard, key, payload);
        return append(request, System.currentTimeMillis() + APPEND_DEADLINE_MS)
                .map(offset -> new QueuePosition(definition.name(), shard, offset));
    }

    /**
     * Fetches committed entries from the shard's owner, which holds the request until an entry at {@code offset}
     * is committed or a short wait passes.
     */
    public Future<FetchResponse> fetch(String queue, int shard, long offset, int max) {
        return requestOwner(queue, shard, QueueNode.FETCH, new FetchRequest(queue, shard, offset, max).toBuffer(), FETCH_TIMEOUT_MS)
                .map(FetchResponse::fromBuffer);
    }

    /**
     * Asks the shard's owner to lease records to a worker, which it holds until there are records to lease or a
     * short wait passes.
     */
    public Future<LeaseResponse> lease(LeaseRequest request) {
        return requestOwner(request.queue(), request.shard(), QueueNode.LEASE, request.toBuffer(), FETCH_TIMEOUT_MS)
                .map(LeaseResponse::fromBuffer);
    }

    /**
     * Tells the shard's owner what a worker did with a record leased to it.
     */
    public Future<Void> settle(SettleRequest request) {
        return requestOwner(request.queue(), request.shard(), QueueNode.SETTLE, request.toBuffer(), REQUEST_TIMEOUT_MS)
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

    public Future<ShardStatus> prepare(String node, PrepareRequest request) {
        return request(node, QueueNode.PREPARE, request.toBuffer(), REQUEST_TIMEOUT_MS).map(ShardStatus::fromBuffer);
    }

    private Future<Long> append(AppendRequest request, long deadline) {
        return requestOwner(request.queue(), request.shard(), QueueNode.APPEND, request.toBuffer(), APPEND_TIMEOUT_MS)
                .map(AppendRequest::decodeReply)
                .recover(e -> isRetryable(e) && System.currentTimeMillis() < deadline
                        ? vertx.timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS).compose(v -> append(request, deadline))
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
