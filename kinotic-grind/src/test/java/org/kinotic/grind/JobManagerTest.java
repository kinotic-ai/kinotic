package org.kinotic.grind;

import io.vertx.core.Vertx;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteSpring;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.core.api.ServerInfo;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.grind.api.services.SystemJob;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cluster's job manager as {@link JobService#deployJobManager} deploys it, on real Ignite nodes
 * that share one in-memory ledger: a system job with a period is run when the manager starts and
 * again every period, a run someone else started moves the schedule, a job whose runs cannot be read
 * leaves the others on schedule, and a node that cannot start a run is followed by the next.
 */
public class JobManagerTest {

    private static final Duration PERIOD = Duration.ofMillis(500);

    // Instantiated before the Vertx instance below: SecurityContext registers its ContextLocal in a
    // static initializer, which Vert.x requires to happen first
    private final SecurityContext securityContext = new SecurityContext(null);

    @TempDir
    Path workDirectory;

    private Vertx vertx;
    private InMemoryJobRunRepository ledger;
    private final List<Ignite> nodes = new ArrayList<>();
    private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        ledger = new InMemoryJobRunRepository();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Ignite node : nodes) {
            Ignition.stop(node.name(), true);
        }
        for (AnnotationConfigApplicationContext context : contexts) {
            context.close();
        }
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    public void aScheduledJobIsRunWhenTheManagerStartsAndAgainEveryPeriod() throws Exception {
        JobService jobService = startNode("node-a", List.of(job("scheduled", PERIOD), job("unscheduled", null)));

        long deployed = System.currentTimeMillis();
        jobService.deployJobManager(nodes.getFirst().cluster().forServers());

        awaitUntil("the scheduled job never ran three times", () -> ledger.systemRunsOf("scheduled").size() >= 3);
        List<JobRun> runs = ledger.systemRunsOf("scheduled");
        long elapsed = System.currentTimeMillis() - deployed;
        // one run when the manager starts and one for every period that has passed since
        assertTrue(runs.size() <= elapsed / PERIOD.toMillis() + 1, runs.size() + " runs in " + elapsed + "ms");
        assertNull(runs.getFirst().getOrganizationId(), "a scheduled run is the system's");
        assertTrue(ledger.systemRunsOf("unscheduled").isEmpty(), "a job with no period is never started by the manager");
    }

    @Test
    public void aRunSomeoneElseStartedMovesTheSchedule() throws Exception {
        Duration period = Duration.ofSeconds(3);
        JobService jobService = startNode("node-a", List.of(job("scheduled", period)));
        // a run an operator started just before the manager came up
        JobRun operators = new JobRun().setId(UUID.randomUUID().toString())
                                       .setName("scheduled")
                                       .setNodeId("node-a")
                                       .setStatus(ExecutionStatus.COMPLETED)
                                       .setStarted(new Date());
        ledger.saveRun(operators);

        jobService.deployJobManager(nodes.getFirst().cluster().forServers());

        awaitUntil("the scheduled job never ran after the operator's run", () -> ledger.systemRunsOf("scheduled").size() >= 2);
        JobRun scheduled = ledger.systemRunsOf("scheduled").get(1);
        long apart = scheduled.getStarted().getTime() - operators.getStarted().getTime();
        assertTrue(apart >= period.toMillis(), "the next run started " + apart + "ms after the operator's");
    }

    @Test
    public void aJobWhoseRunsCannotBeReadLeavesTheOthersOnSchedule() throws Exception {
        ledger.unreadableJob = "unreadable";
        JobService jobService = startNode("node-a", List.of(job("unreadable", PERIOD), job("scheduled", PERIOD)));

        jobService.deployJobManager(nodes.getFirst().cluster().forServers());

        // the wake that failed to decide the unreadable job set the timer again, or these would stop at one
        awaitUntil("the scheduled job stopped running", () -> ledger.systemRunsOf("scheduled").size() >= 3);
        assertTrue(ledger.systemRunsOf("unreadable").isEmpty(), "a job is not started without its latest run");
    }

    @Test
    public void aNodeThatCannotStartTheRunIsFollowedByTheNext() throws Exception {
        AtomicBoolean broken = new AtomicBoolean();
        SystemJob working = job("scheduled", PERIOD);
        SystemJob breaking = () -> {
            if (broken.get()) {
                throw new IllegalStateException("node-b cannot build the job");
            }
            return working.createJobDefinition();
        };
        JobService jobService = startNode("node-a", List.of(working));
        JobService other = startNode("node-b", List.of(breaking));
        jobService.deployJobManager(nodes.getFirst().cluster().forServers());
        other.deployJobManager(nodes.getLast().cluster().forServers());

        broken.set(true);
        int before = ledger.systemRunsOf("scheduled").size();

        // every other run is node-b's turn; one it left unstarted would leave the job waiting a minute
        awaitUntil("the runs stopped once node-b could not start them", () -> ledger.systemRunsOf("scheduled").size() >= before + 4);
        List<JobRun> runs = ledger.systemRunsOf("scheduled");
        // the first of them may have been node-b's, started before it broke
        for (JobRun run : runs.subList(before + 1, runs.size())) {
            assertEquals("node-a", run.getNodeId());
        }
    }

    private static SystemJob job(String name, Duration period) {
        return () -> {
            JobDefinition ret = JobDefinition.create("A system job of the test").name(name).version("1")
                                             .task(Tasks.fromRunnable("work", () -> { }));
            if (period != null) {
                ret.every(period);
            }
            return ret;
        };
    }

    /**
     * Starts an Ignite node whose Spring context holds what a server node's holds for the manager: a
     * job service with the given system jobs, the shared ledger and Vert.x.
     */
    private JobService startNode(String name, List<SystemJob> systemJobs) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        contexts.add(context);
        // registered as they are, since the in-memory ledger has no index to verify on start
        context.getBeanFactory().registerSingleton("jobRunRepository", ledger);
        context.getBeanFactory().registerSingleton("vertx", vertx);
        context.registerBean(DefaultJobService.class,
                             () -> new DefaultJobService(ledger, new ObjectMapper(), vertx,
                                                         () -> new ServerInfo(name, "grind test node"), systemJobs));
        context.refresh();

        // a discovery port range of the test's own, so the nodes find each other and no other module's test cluster
        IgniteConfiguration configuration = new IgniteConfiguration()
                .setIgniteInstanceName(name)
                .setLocalHost("127.0.0.1")
                .setMetricsLogFrequency(0)
                .setWorkDirectory(workDirectory.resolve(name).toString())
                .setDiscoverySpi(new TcpDiscoverySpi().setLocalPort(48700)
                                                      .setIpFinder(new TcpDiscoveryVmIpFinder().setAddresses(List.of("127.0.0.1:48700..48709"))));
        nodes.add(IgniteSpring.start(configuration, context));
        return context.getBean(JobService.class);
    }

    private static void awaitUntil(String failure, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), failure);
    }

}
