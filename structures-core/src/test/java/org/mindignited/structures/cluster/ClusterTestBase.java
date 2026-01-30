package org.mindignited.structures.cluster;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * External docker-compose cluster base that does NOT start an Ignite node in the test JVM.
 *
 * The cluster is formed only by the docker-compose containers (nodes 1-3). Tests validate:
 * - Cluster membership via container logs (last "servers=N" from "Topology snapshot" lines)
 * - Cache eviction deterministically via host-mounted eviction CSV files
 * - Functional behavior via HTTP APIs
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ClusterTestBase {

    protected static final int HEALTH_PORT = 9090;
    protected static final int OPENAPI_PORT = 8080;
    protected static final int GRAPHQL_PORT = 4000;

    protected static final List<String> STRUCTURES_SERVICES =
            List.of("structures-node1", "structures-node2", "structures-node3");

    protected static final String DEFAULT_USERNAME = "admin";
    protected static final String DEFAULT_PASSWORD = "structures";

    private static final Pattern SERVERS_PATTERN = Pattern.compile("servers=(\\d+)");
    private static final Pattern TOPOLOGY_LINE_PATTERN = Pattern.compile("Topology snapshot.*");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    protected final String basicAuthHeader = Base64.getEncoder()
            .encodeToString((DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD).getBytes(StandardCharsets.UTF_8));

    protected ElasticsearchContainer elasticsearchContainer;
    protected DockerComposeContainer<?> environment;

    protected String elasticsearchHost;
    protected int elasticsearchPort;

    @BeforeAll
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    public void setupEnvironment() {
        ensureHostDirectories();
        startElasticsearch();
        waitForElasticsearchReady(elasticsearchHost, elasticsearchPort, Duration.ofMinutes(3));
        startDockerComposeCluster();
        waitForAllNodesHealthy(Duration.ofMinutes(4));
        awaitServersCountAllNodesAtLeast(3, Duration.ofMinutes(6));
    }

    @AfterAll
    public void teardownEnvironment() {
        if (environment != null) {
            try {
                environment.stop();
            } catch (Exception e) {
                log.warn("Failed stopping docker-compose environment", e);
            }
        }
        if (elasticsearchContainer != null) {
            try {
                elasticsearchContainer.stop();
            } catch (Exception e) {
                log.warn("Failed stopping Elasticsearch container", e);
            }
        }
    }

    @AfterEach
    void cleanupEvictionFiles() {
        Path evictionDir = getEvictionDataDir();
        if (!Files.exists(evictionDir)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(evictionDir, "evictions-*.csv")) {
            for (Path p : stream) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed deleting eviction file {}: {}", p, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed cleaning eviction directory {}: {}", evictionDir.toAbsolutePath(), e.getMessage());
        }
    }

    protected void stopNode(int index) {
        String service = serviceNameFor(index);
        ContainerState container = getContainerState(service);
        var docker = container.getDockerClient();
        String containerId = Objects.requireNonNull(container.getContainerId(), "Container ID must not be null");
        docker.stopContainerCmd(containerId).withTimeout(30).exec();
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void startNode(int index) {
        String service = serviceNameFor(index);
        ContainerState container = getContainerState(service);
        var docker = container.getDockerClient();
        String containerId = Objects.requireNonNull(container.getContainerId(), "Container ID must not be null");
        docker.startContainerCmd(containerId).exec();
    }

    protected void waitForNodeHealthy(int nodeIndex, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String healthUrl = getHealthUrl(nodeIndex);
        while (System.currentTimeMillis() < deadline) {
            if (ClusterHealthVerifier.isNodeHealthy(healthUrl)) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for node health", e);
            }
        }
        throw new IllegalStateException("Node " + nodeIndex + " did not become healthy within " + timeout + " at " + healthUrl);
    }

    protected void waitForAllNodesHealthy(Duration timeout) {
        for (int i = 1; i <= STRUCTURES_SERVICES.size(); i++) {
            waitForNodeHealthy(i, timeout);
        }
    }

    protected String getOpenApiUrl(int nodeIndex) {
        String service = serviceNameFor(nodeIndex);
        int servicePort = portFor(nodeIndex, OPENAPI_PORT);
        return String.format("http://%s:%d/api", resolveServiceHost(service, servicePort), servicePort);
    }

    protected String getHealthUrl(int nodeIndex) {
        String service = serviceNameFor(nodeIndex);
        int servicePort = portFor(nodeIndex, HEALTH_PORT);
        return String.format("http://%s:%d/health", resolveServiceHost(service, servicePort), servicePort);
    }

    protected String getGraphQLUrl(int nodeIndex) {
        String service = serviceNameFor(nodeIndex);
        int servicePort = portFor(nodeIndex, GRAPHQL_PORT);
        return String.format("http://%s:%d/graphql", resolveServiceHost(service, servicePort), servicePort);
    }

    /**
     * Structure metadata endpoint used by the cluster docs and existing tests.
     */
    protected String getStructureEndpoint(int nodeIndex, String structureId) {
        return getOpenApiUrl(nodeIndex) + "/structures/" + structureId;
    }

    protected String getSearchEndpoint(int nodeIndex, String structureId) {
        int firstDotIndex = structureId.indexOf('.');
        if (firstDotIndex == -1) {
            throw new IllegalArgumentException("Invalid structure ID format: " + structureId + ". Expected format: applicationId.structureName");
        }
        String applicationId = structureId.substring(0, firstDotIndex);
        String structureName = structureId.substring(firstDotIndex + 1);
        return getOpenApiUrl(nodeIndex) + "/" + applicationId + "/" + structureName + "/search";
    }

    protected HttpResponse<String> httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basicAuthHeader)
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> awaitGetOk(String url, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int lastStatus = -1;
        String lastBody = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> resp = httpGet(url);
                lastStatus = resp.statusCode();
                lastBody = resp.body();
                if (resp.statusCode() == HttpURLConnection.HTTP_OK) {
                    return resp;
                }
            } catch (Exception e) {
                lastBody = e.getMessage();
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for HTTP 200 from " + url +
                " (lastStatus=" + lastStatus + ", lastBody=" + (lastBody == null ? "null" : lastBody) + ")");
    }

    protected HttpResponse<String> httpPost(String url, String contentType, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basicAuthHeader)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPut(String url, String contentType, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basicAuthHeader)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected void warmCacheWithSearch(int nodeIndex, String structureId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception last = null;

        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> resp = httpPost(getSearchEndpoint(nodeIndex, structureId), "text/plain", "*");
                if (resp.statusCode() == HttpURLConnection.HTTP_OK) {
                    return;
                }
                last = new IOException("Unexpected status " + resp.statusCode() + " for search on node " + nodeIndex);
            } catch (Exception e) {
                last = e instanceof Exception ? (Exception) e : new Exception(e);
            }
            Thread.sleep(500);
        }

        throw new AssertionError("Timed out warming cache via search on node " + nodeIndex + " for " + structureId, last);
    }

    /**
     * Host eviction directory. Matches docker-compose bind mount in `cluster-test-compose.yml`.
     */
    protected Path getEvictionDataDir() {
        return Paths.get(
                System.getProperty(
                        "structures.test.evictionDataDir",
                        System.getProperty("user.home") + "/structures/eviction-data"
                )
        );
    }

    protected void awaitCacheEvictionProcessed(long beforeTimestamp, Duration timeout) {
        Path evictionDir = getEvictionDataDir();
        List<String> expectedWriters = new ArrayList<>(STRUCTURES_SERVICES);

        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            int writersWithNewRecords = 0;
            for (String writer : expectedWriters) {
                Path csv = evictionDir.resolve("evictions-" + writer + ".csv");
                long maxTimestamp = readMaxEvictionTimestamp(csv, beforeTimestamp);
                if (maxTimestamp > beforeTimestamp) {
                    writersWithNewRecords++;
                }
            }

            if (writersWithNewRecords >= expectedWriters.size()) {
                return;
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for cache eviction", e);
            }
        }

        throw new AssertionError("Cache eviction not observed on all nodes within " + timeout + " (evictionDir=" + evictionDir.toAbsolutePath() + ")");
    }

    private long readMaxEvictionTimestamp(Path csvFile, long sinceTimestamp) {
        if (!Files.exists(csvFile)) {
            return -1;
        }
        try {
            long max = -1;
            for (String line : Files.readAllLines(csvFile, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                int firstComma = line.indexOf(',');
                if (firstComma <= 0) {
                    continue;
                }
                String tsStr = line.substring(0, firstComma).trim();
                long ts;
                try {
                    ts = Long.parseLong(tsStr);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (ts > sinceTimestamp && ts > max) {
                    max = ts;
                }
            }
            return max;
        } catch (IOException e) {
            return -1;
        }
    }

    protected int getLastServersCountFromLogs(int nodeIndex) {
        String service = serviceNameFor(nodeIndex);
        ContainerState container = getContainerState(service);
        String logs = container.getLogs();
        Matcher m = SERVERS_PATTERN.matcher(logs);
        int last = 0;
        while (m.find()) {
            last = Integer.parseInt(m.group(1));
        }
        return last;
    }

    protected void awaitServersCount(int nodeIndex, int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            int last = getLastServersCountFromLogs(nodeIndex);
            if (last == expected) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for servers count", e);
            }
        }
        throw new AssertionError(buildServersTimeoutMessage(nodeIndex, "servers=" + expected));
    }

    protected void awaitServersCountAllNodes(int expected, Duration timeout) {
        for (int i = 1; i <= STRUCTURES_SERVICES.size(); i++) {
            awaitServersCount(i, expected, timeout);
        }
    }

    protected void awaitServersCountAtLeast(int nodeIndex, int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            int last = getLastServersCountFromLogs(nodeIndex);
            if (last >= expected) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for servers count", e);
            }
        }
        throw new AssertionError(buildServersTimeoutMessage(nodeIndex, "servers>=" + expected));
    }

    protected void awaitServersCountAllNodesAtLeast(int expected, Duration timeout) {
        for (int i = 1; i <= STRUCTURES_SERVICES.size(); i++) {
            awaitServersCountAtLeast(i, expected, timeout);
        }
    }

    private String buildServersTimeoutMessage(int nodeIndex, String expectation) {
        String service = serviceNameFor(nodeIndex);
        ContainerState container = getContainerState(service);
        String logs = container.getLogs();

        // Include some helpful log context (last topology line + last few lines).
        String lastTopology = null;
        Matcher topo = TOPOLOGY_LINE_PATTERN.matcher(logs);
        while (topo.find()) {
            lastTopology = topo.group();
        }

        int lastServers = 0;
        Matcher m = SERVERS_PATTERN.matcher(logs);
        while (m.find()) {
            lastServers = Integer.parseInt(m.group(1));
        }

        String tail;
        int maxTailChars = 8_000;
        if (logs.length() <= maxTailChars) {
            tail = logs;
        } else {
            tail = logs.substring(logs.length() - maxTailChars);
        }

        return "Timed out waiting for node " + nodeIndex + " (" + service + ") to satisfy " + expectation +
                " (lastServers=" + lastServers + ", lastTopologyLine=" + lastTopology + ")\n" +
                "---- container log tail ----\n" + tail + "\n" +
                "---- end log tail ----";
    }

    private void ensureHostDirectories() {
        try {
            Path sharedFsDir = Paths.get(System.getProperty("user.home"), "structures", "sharedfs");
            Path evictionDir = Paths.get(System.getProperty("user.home"), "structures", "eviction-data");

            Files.createDirectories(sharedFsDir);
            Files.createDirectories(evictionDir);

            // Start from a clean slate:
            // - SHAREDFS discovery writes files under the shared FS; stale data can prevent clustering
            // - eviction CSVs should not leak across runs
            deleteDirectoryContents(sharedFsDir);
            deleteDirectoryContents(evictionDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed creating required host directories under ${HOME}/structures", e);
        }
    }

    @SuppressWarnings("resource") // Stopped in @AfterAll teardownEnvironment()
    private void startElasticsearch() {
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        elasticsearchContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.1")
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false");

        // Workaround for https://github.com/elastic/elasticsearch/issues/118583 (Mac aarch64)
        if (osName != null && osName.startsWith("Mac") && "aarch64".equals(osArch)) {
            elasticsearchContainer.withEnv("_JAVA_OPTIONS", "-XX:UseSVE=0");
        }

        elasticsearchContainer.start();

        elasticsearchHost = elasticsearchContainer.getHost();
        elasticsearchPort = elasticsearchContainer.getMappedPort(9200);

        log.info("Elasticsearch started at {}:{}", elasticsearchHost, elasticsearchPort);
    }

    private static void waitForElasticsearchReady(String host, int port, Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        URI uri = URI.create("http://" + host + ":" + port + "/_cluster/health?wait_for_status=yellow&timeout=1s");
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Exception last = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == HttpURLConnection.HTTP_OK) {
                    return;
                }
                last = new IOException("Unexpected status: " + resp.statusCode());
            } catch (Exception e) {
                last = e;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Elasticsearch readiness", e);
            }
        }

        throw new IllegalStateException(
                "Elasticsearch did not become ready within " + timeout + " at " + uri + (last != null ? " (last error: " + last + ")" : "")
        );
    }

    private void startDockerComposeCluster() {
        Path composeFile = resolveComposeFile();

        String containerElasticsearchHost = "host.docker.internal";
        int containerElasticsearchPort = elasticsearchPort;

        try {
            @SuppressWarnings("resource") // Stopped in @AfterAll teardownEnvironment()
            DockerComposeContainer<?> env = new DockerComposeContainer<>(composeFile.toFile())
                    .withLocalCompose(true)
                    .withTailChildContainers(true)
                    .withEnv("STRUCTURES_TEST_INSTANCE_HOST", "host.docker.internal")
                    .withEnv("STRUCTURES_ELASTIC_SCHEME", "http")
                    .withEnv("STRUCTURES_ELASTIC_HOST", containerElasticsearchHost)
                    .withEnv("STRUCTURES_ELASTIC_PORT", String.valueOf(containerElasticsearchPort));

            env.start();
            environment = env;
        } catch (Exception e) {
            throw new RuntimeException("Failed to start docker-compose environment", e);
        }
    }

    private static Path resolveComposeFile() {
        // Prefer classpath lookup (works regardless of Gradle/IDE working directory).
        try {
            var url = ClusterTestBase.class
                    .getClassLoader()
                    .getResource("docker-compose/cluster-test-compose.yml");
            if (url != null) {
                Path p = Paths.get(url.toURI());
                if (Files.exists(p)) {
                    return p;
                }
            }
        } catch (Exception ignored) {
            // fall through to filesystem fallbacks
        }

        // Fallbacks for common working directories.
        Path[] candidates = new Path[] {
                Paths.get("src", "test", "resources", "docker-compose", "cluster-test-compose.yml").toAbsolutePath(),
                Paths.get("structures-core", "src", "test", "resources", "docker-compose", "cluster-test-compose.yml").toAbsolutePath()
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cluster compose file not found. Tried: " + java.util.Arrays.toString(candidates));
    }

    private static void deleteDirectoryContents(Path rootDir) throws IOException {
        if (rootDir == null || !Files.exists(rootDir)) {
            return;
        }
        if (!Files.isDirectory(rootDir)) {
            Files.deleteIfExists(rootDir);
            return;
        }

        // Delete children first, but keep the root directory.
        try (var walk = java.nio.file.Files.walk(rootDir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .filter(p -> !p.equals(rootDir))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
        }
    }

    private ContainerState getContainerState(String service) {
        if (environment == null) {
            throw new IllegalStateException("Docker compose environment not started");
        }
        return environment.getContainerByServiceName(service + "_1")
                .orElseThrow(() -> new IllegalArgumentException("No container found for service " + service));
    }

    private String resolveServiceHost(String service, int servicePort) {
        return environment.getServiceHost(service, servicePort);
    }

    private static int portFor(int nodeIndex, int basePort) {
        if (nodeIndex < 1 || nodeIndex > STRUCTURES_SERVICES.size()) {
            throw new IllegalArgumentException("Invalid node index: " + nodeIndex + ". Expected 1-" + STRUCTURES_SERVICES.size());
        }
        return basePort + nodeIndex;
    }

    private static String serviceNameFor(int index) {
        if (index < 1 || index > STRUCTURES_SERVICES.size()) {
            throw new IllegalArgumentException("Invalid container node index: " + index + ". Expected 1-" + STRUCTURES_SERVICES.size());
        }
        return STRUCTURES_SERVICES.get(index - 1);
    }
}

