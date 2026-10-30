package org.kinotic.queue.internal;

import org.awaitility.Awaitility;
import org.kinotic.queue.internal.log.QueueLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queue nodes a test runs, each keeping its data in a directory of its own under the test's directory. Closing
 * the cluster stops every node still running.
 */
public final class QueueTestCluster implements AutoCloseable {

    private final Path directory;
    private final int replicationFactor;
    private final Map<String, Object> properties;
    private final List<QueueTestNode> nodes = new ArrayList<>();

    public QueueTestCluster(Path directory, int replicationFactor) {
        this(directory, replicationFactor, Map.of());
    }

    /**
     * @param properties further {@code kinotic.queue} settings every node starts with, by property name
     */
    public QueueTestCluster(Path directory, int replicationFactor, Map<String, Object> properties) {
        this.directory = directory;
        this.replicationFactor = replicationFactor;
        this.properties = properties;
    }

    /**
     * Starts a node keeping its data in the directory of its name, which holds its data from an earlier start.
     */
    public QueueTestNode start(String name) throws Exception {
        return start(name, name);
    }

    /**
     * Starts a node keeping its data in the named directory, which may hold the data of another node.
     */
    public QueueTestNode start(String name, String directoryName) throws Exception {
        QueueTestNode ret = QueueTestNode.start(name, directory.resolve(directoryName), replicationFactor, properties);
        nodes.add(ret);
        return ret;
    }

    /**
     * Stops the node gracefully; see {@link QueueTestNode#close()}.
     */
    public void stop(QueueTestNode node) throws Exception {
        nodes.remove(node);
        node.close();
    }

    /**
     * Stops the node as a dying process does; see {@link QueueTestNode#crash()}.
     */
    public void crash(QueueTestNode node) throws Exception {
        nodes.remove(node);
        node.crash();
    }

    /**
     * @return the running nodes, in the order they started
     */
    public List<QueueTestNode> nodes() {
        return List.copyOf(nodes);
    }

    /**
     * Waits until every running node's copy of the queue's shard counts the copy on {@code voter} toward commits, with
     * no change of the copies counting under way.
     */
    public void awaitVoting(String queue, int shard, QueueTestNode voter) {
        String storageId = QueueLog.storageId(voter.dataDirectory());
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            for (QueueTestNode node : nodes()) {
                String latest = latestMembership(node.dataDirectory().resolve(queue).resolve("shards").resolve(String.valueOf(shard)));
                assertTrue(latest.matches(".* \\S*" + storageId + "\\S* -"), "node " + node.name() + " counts " + latest);
            }
        });
    }

    @Override
    public void close() throws Exception {
        for (QueueTestNode node : nodes) {
            node.close();
        }
    }

    // The last membership change the shard's manifest lists: offset, epoch, voting copies, joining copies
    private static String latestMembership(Path shard) {
        String ret;
        try {
            List<String> memberships = Files.readAllLines(shard.resolve("segments")).stream().filter(line -> line.startsWith("membership ")).toList();
            ret = memberships.isEmpty() ? "" : memberships.getLast();
        } catch (Exception e) {
            ret = "";
        }
        return ret;
    }
}
