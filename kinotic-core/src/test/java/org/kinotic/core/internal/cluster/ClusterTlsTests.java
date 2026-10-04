package org.kinotic.core.internal.cluster;

import org.apache.ignite.Ignition;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.client.SslMode;
import org.apache.ignite.configuration.ClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Starts Kinotic nodes in their own processes with kinotic.clusterTls enabled and checks that nodes holding a trusted
 * certificate form a cluster, that a node whose certificate the cluster does not trust never joins it, and that the
 * nodes' thin client port only serves clients holding a trusted certificate. Skipped where the JDK's keytool is
 * missing.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class ClusterTlsTests {

    private static final String PASSWORD = "changeit";

    @TempDir
    private Path directory;

    private final List<Process> processes = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        processes.forEach(Process::destroyForcibly);
    }

    @Test
    public void onlyNodesWithATrustedCertificateJoinTheCluster() throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        assumeTrue(Files.isExecutable(keytool), "keytool is not available");
        Path trusted = directory.resolve("trusted.p12");
        Path intruder = directory.resolve("intruder.p12");
        Path trust = directory.resolve("trust.p12");
        Path certificate = directory.resolve("trusted.crt");
        keytool(keytool, "-genkeypair", "-alias", "node", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=kinotic-node", "-storetype", "PKCS12", "-keystore", trusted.toString(), "-storepass", PASSWORD);
        keytool(keytool, "-genkeypair", "-alias", "node", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=intruder", "-storetype", "PKCS12", "-keystore", intruder.toString(), "-storepass", PASSWORD);
        keytool(keytool, "-exportcert", "-alias", "node", "-keystore", trusted.toString(), "-storepass", PASSWORD,
                "-file", certificate.toString());
        keytool(keytool, "-importcert", "-noprompt", "-alias", "node", "-file", certificate.toString(), "-storetype", "PKCS12",
                "-keystore", trust.toString(), "-storepass", PASSWORD);

        AtomicInteger first = start("first", 0, trusted, trust);
        AtomicInteger second = start("second", 1, trusted, trust);
        awaitServers(first, 2);
        awaitServers(second, 2);

        assertFalse(connects(new ClientConfiguration()), "a thin client connected without TLS");
        assertFalse(connects(tlsClient(intruder, trust)), "a thin client with an untrusted certificate connected");
        assertTrue(connects(tlsClient(trusted, trust)), "a thin client with a trusted certificate could not connect");

        AtomicInteger rejected = start("intruder", 2, intruder, trust);
        // Long enough for the intruder to have joined had its certificate been accepted
        Thread.sleep(30_000);

        assertEquals(2, first.get(), "a node without a trusted certificate joined the cluster");
        assertTrue(rejected.get() <= 1, "the intruder saw " + rejected.get() + " servers");
    }

    // Starts a node whose ports are offset from the defaults, so the processes share the machine; returns the number of
    // servers the node last reported, -1 until it reports
    private AtomicInteger start(String name, int index, Path keyStore, Path trustStore) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(arg -> !arg.startsWith("-javaagent")).toList());
        command.addAll(List.of("-cp", System.getProperty("java.class.path"), ClusterTlsTestNode.class.getName(),
                               "--spring.profiles.active=test",
                               "--kinotic.ignite.discoveryType=SHAREDFS",
                               "--kinotic.ignite.sharedFsPath=" + directory.resolve("sharedfs"),
                               "--kinotic.ignite.discoveryPort=" + (47600 + index),
                               "--kinotic.ignite.communicationPort=" + (47200 + index),
                               "--kinotic.ignite.workDirectory=" + directory.resolve(name + "-work"),
                               "--kinotic.clusterTls.enabled=true",
                               "--kinotic.clusterTls.keyStorePath=" + keyStore,
                               "--kinotic.clusterTls.keyStorePassword=" + PASSWORD,
                               "--kinotic.clusterTls.trustStorePath=" + trustStore,
                               "--kinotic.clusterTls.trustStorePassword=" + PASSWORD));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        processes.add(process);
        AtomicInteger ret = new AtomicInteger(-1);
        Thread.ofPlatform().daemon().start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("servers=")) {
                        ret.set(Integer.parseInt(line.substring("servers=".length())));
                    }
                }
            } catch (IOException e) {
                // The process ended
            }
        });
        return ret;
    }

    private static ClientConfiguration tlsClient(Path keyStore, Path trustStore) {
        return new ClientConfiguration().setSslMode(SslMode.REQUIRED)
                                        .setSslClientCertificateKeyStorePath(keyStore.toString())
                                        .setSslClientCertificateKeyStoreType("PKCS12")
                                        .setSslClientCertificateKeyStorePassword(PASSWORD)
                                        .setSslTrustCertificateKeyStorePath(trustStore.toString())
                                        .setSslTrustCertificateKeyStoreType("PKCS12")
                                        .setSslTrustCertificateKeyStorePassword(PASSWORD);
    }

    // Whether a thin client with the configuration can read the cache names from one of the nodes' client ports
    private static boolean connects(ClientConfiguration configuration) {
        boolean ret;
        try (IgniteClient client = Ignition.startClient(configuration.setAddresses("127.0.0.1:10800..10810").setTimeout(5_000))) {
            client.cacheNames();
            ret = true;
        } catch (Exception e) {
            ret = false;
        }
        return ret;
    }

    private static void awaitServers(AtomicInteger servers, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (servers.get() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertEquals(expected, servers.get(), "the cluster did not form");
    }

    private static void keytool(Path keytool, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(keytool.toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
}
