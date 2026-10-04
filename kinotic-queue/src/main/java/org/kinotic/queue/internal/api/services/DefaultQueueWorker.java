package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.QueueRecord;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkItem;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.message.LeaseRequest;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;
import org.kinotic.queue.internal.cluster.message.LeasedEntry;
import org.kinotic.queue.internal.cluster.message.Settlement;
import org.kinotic.queue.internal.cluster.message.SettleRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * One worker of a group. Each shard's owner leases records to it, and the worker settles each record with the owner
 * that leased it.
 */
public class DefaultQueueWorker extends ShardPullStream<WorkItem, LeaseResponse> implements QueueWorker {

    private final QueueClusterClient client;
    private final String queue;
    private final String groupName;
    private final WorkerOptions options;
    private final int shardCount;
    private final String workerId = UUID.randomUUID().toString();
    // Records leased to this worker and not yet settled, whether or not they reached the handler
    private final Set<QueuePosition> held = new HashSet<>();
    // Records asked for by lease requests still on their way
    private int requested;

    private DefaultQueueWorker(Context context, QueueClusterClient client, String queue, int shardCount, String groupName, WorkerOptions options) {
        super(context, shardCount, "worker of group " + groupName + " of queue " + queue);
        this.client = client;
        this.queue = queue;
        this.groupName = groupName;
        this.options = options;
        this.shardCount = shardCount;
        context.runOnContext(v -> pullAll());
    }

    /**
     * Opens a worker of the group. A group starting at the latest record has its position stored on every shard
     * before the worker opens, so it receives every record appended after it opened.
     */
    static Future<QueueWorker> open(Context context, QueueClusterClient client, QueueDefinition definition, String groupName,
                                    WorkerOptions options) {
        Future<Void> joined = Future.succeededFuture();
        if (options.startPosition() == StartPosition.LATEST) {
            joined = Future.all(IntStream.range(0, definition.shardCount())
                                         .mapToObj(shard -> client.joinGroup(definition.name(), shard, groupName, StartPosition.LATEST))
                                         .toList())
                           .mapEmpty();
        }
        return joined.map(v -> new DefaultQueueWorker(context, client, definition.name(), definition.shardCount(), groupName, options));
    }

    @Override
    public Future<Void> accept(WorkItem item) {
        return settle(item, Settlement.ACCEPT);
    }

    @Override
    public Future<Void> release(WorkItem item) {
        return settle(item, Settlement.RELEASE);
    }

    @Override
    public Future<Void> reject(WorkItem item) {
        return settle(item, Settlement.REJECT);
    }

    @Override
    public Future<Void> renew(WorkItem item) {
        return settle(item, Settlement.RENEW);
    }

    @Override
    public Future<Void> close() {
        Set<QueuePosition> undelivered = new HashSet<>();
        end().forEach(item -> undelivered.add(item.record().position()));
        List<Future<Void>> released = List.copyOf(held).stream()
                                          .map(position -> settle(position, undelivered.contains(position) ? Settlement.RETURN
                                                                                                           : Settlement.RELEASE))
                                          .toList();
        // A record that fails to go back returns to the group once its lease expires
        return Future.join(released).otherwiseEmpty().mapEmpty();
    }

    // Every shard is asked whenever the worker has room, since a shard with nothing to lease holds its request open and
    // reserving room for it would starve the shards that have records
    @Override
    protected boolean canPull(int shard) {
        return held.size() < options.prefetch();
    }

    @Override
    protected Future<LeaseResponse> pull(int shard) {
        int room = Math.max(0, options.prefetch() - held.size() - requested);
        int wanted = Math.max(1, (room + shardCount - 1) / shardCount);
        requested += wanted;
        LeaseRequest request = new LeaseRequest(queue, shard, groupName, workerId, wanted,
                                                options.leaseDuration().toMillis(), options.startPosition());
        return client.lease(request).onComplete(ar -> requested -= wanted);
    }

    @Override
    protected void onPulled(int shard, LeaseResponse response) {
        for (LeasedEntry leased : response.leased()) {
            QueuePosition position = new QueuePosition(queue, shard, leased.entry().offset());
            held.add(position);
            if (isEnded()) {
                // Leased after close(), so it goes straight back to the group
                settle(position, Settlement.RETURN);
            } else {
                push(new WorkItem(new QueueRecord(position, leased.entry().key(), leased.entry().payload()), leased.deliveryCount()));
            }
        }
    }

    @Override
    protected void onDelivered(WorkItem item) {
        // The item stays held until it is settled
    }

    private Future<Void> settle(WorkItem item, Settlement settlement) {
        QueuePosition position = item.record().position();
        if (!held.contains(position)) {
            return Future.failedFuture(new IllegalArgumentException("Record " + position + " is not held by this worker"));
        }
        return settle(position, settlement);
    }

    private Future<Void> settle(QueuePosition position, Settlement settlement) {
        return client.settle(new SettleRequest(queue, position.shard(), groupName, workerId, settlement, position.offset()))
                     .onComplete(ar -> {
                         // A renewed item stays held; any other settlement, or a failure, means it is no longer leased here
                         if (settlement != Settlement.RENEW || ar.failed()) {
                             held.remove(position);
                             if (!isEnded()) {
                                 pullAll();
                             }
                         }
                     });
    }
}
