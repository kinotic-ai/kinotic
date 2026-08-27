package org.kinotic.grind.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.DefaultSystemParticipant;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.Result;
import org.kinotic.grind.api.model.ResultType;
import org.kinotic.grind.api.model.StepRecord;
import org.kinotic.grind.api.model.Tasks;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link DefaultJobMonitoringService} authorization and delegation against real
 * {@link DefaultJobService} executions: organization participants may only view runs their
 * organization owns, system participants may view any, and watch serves live runs only.
 */
class DefaultJobMonitoringServiceTest {

    private static final Participant ACME_USER =
            new DefaultOrganizationParticipant("user-1", "acme", Map.of(), List.of("USER"));
    private static final Participant GLOBEX_USER =
            new DefaultOrganizationParticipant("user-2", "globex", Map.of(), List.of("USER"));
    private static final Participant PLATFORM_OPERATOR =
            new DefaultSystemParticipant("operator-1", Map.of(), List.of("ADMIN"));

    private static SecurityContext securityContext;
    private static Vertx vertx;

    private AnnotationConfigApplicationContext appCtx;
    private InMemoryJobRunService records;
    private DefaultJobService jobService;
    private DefaultJobMonitoringService service;

    @BeforeAll
    static void setup() {
        // SecurityContext registers its ContextLocal at class load, which must happen
        // before any Vertx instance is created
        securityContext = new SecurityContext();
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void tearDown() {
        vertx.close();
    }

    @BeforeEach
    void setupServices() {
        appCtx = new AnnotationConfigApplicationContext();
        appCtx.refresh();
        records = new InMemoryJobRunService();
        jobService = new DefaultJobService(records, new ObjectMapper());
        jobService.setApplicationContext(appCtx);
        service = new DefaultJobMonitoringService(records, jobService, securityContext);
    }

    @AfterEach
    void tearDownServices() {
        appCtx.close();
    }

    @Test
    void organizationParticipantSeesOnlyItsOwnRuns() throws Throwable {
        executeAndAwait(twoTaskJob("acme deploy"), JobOwner.ofOrganization("acme"));
        executeAndAwait(twoTaskJob("globex deploy"), JobOwner.ofOrganization("globex"));
        executeAndAwait(twoTaskJob("platform maintenance"), null);

        Page<JobRun> page = callAs(ACME_USER, () -> service.findJobRuns(Pageable.create(0, 50, null)));

        assertEquals(1, page.getContent().size());
        assertEquals("acme deploy", page.getContent().getFirst().getName());
    }

    @Test
    void systemParticipantSeesEveryRun() throws Throwable {
        executeAndAwait(twoTaskJob("acme deploy"), JobOwner.ofOrganization("acme"));
        executeAndAwait(twoTaskJob("globex deploy"), JobOwner.ofOrganization("globex"));
        executeAndAwait(twoTaskJob("platform maintenance"), null);

        Page<JobRun> page = callAs(PLATFORM_OPERATOR, () -> service.findJobRuns(Pageable.create(0, 50, null)));

        assertEquals(3, page.getContent().size());
    }

    @Test
    void organizationParticipantMayNotReadAnotherOrganizationsRun() throws Throwable {
        JobRunHandle globexRun = executeAndAwait(twoTaskJob("globex deploy"), JobOwner.ofOrganization("globex"));
        JobRunHandle platformRun = executeAndAwait(twoTaskJob("platform maintenance"), null);

        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.findJobRun(globexRun.getJobRunId())));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.findJobRun(platformRun.getJobRunId())));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.findSteps(globexRun.getJobRunId(),
                                                                            Pageable.create(0, 50, null))));
    }

    @Test
    void unknownRunFails() {
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(PLATFORM_OPERATOR, () -> service.findJobRun("missing-run")));
    }

    @Test
    void stepRecordsServeTheRunsStepLedger() throws Throwable {
        JobRunHandle execution = executeAndAwait(twoTaskJob("acme deploy"), JobOwner.ofOrganization("acme"));

        Page<StepRecord> page = callAs(ACME_USER, () -> service.findSteps(execution.getJobRunId(),
                                                                                Pageable.create(0, 50, null)));

        // the root job and both tasks, all terminal since the run completed
        assertEquals(3, page.getContent().size());
        assertTrue(page.getContent().stream().allMatch(record -> record.getStatus() == ExecutionStatus.COMPLETED));
    }

    @Test
    void watchStreamsAnExecutingRunToItsOwner() throws Throwable {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition def = JobDefinition.create("gated deploy").name("gated-deploy")
            .task(Tasks.fromCallable("gate", () -> {
                gateReached.countDown();
                release.await();
                return "opened";
            }))
            .task(Tasks.fromCallable("after gate", () -> "done"));

        JobRunHandle execution = jobService.execute(def, JobOwner.ofOrganization("acme"));
        execution.getResults().subscribeOn(Schedulers.boundedElastic()).subscribe(result -> { }, throwable -> { });
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");

        Flux<Result<?>> watchFlux = onContextAs(ACME_USER, () -> service.watch(execution.getJobRunId()));
        CountDownLatch sawGateStart = new CountDownLatch(1);
        CompletableFuture<List<Result<?>>> watched = watchFlux
                .doOnNext(result -> {
                    if(result.getResultType() == ResultType.STEP_STARTED && "gate".equals(result.getValue())){
                        sawGateStart.countDown();
                    }
                })
                .collectList().toFuture();

        // release only after the watcher replayed the gate start, proving it attached to the live run
        assertTrue(sawGateStart.await(10, TimeUnit.SECONDS), "watcher did not replay the run's history");
        release.countDown();

        List<Result<?>> results = watched.get(15, TimeUnit.SECONDS);
        assertTrue(results.stream().anyMatch(result -> result.getResultType() == ResultType.STEP_STARTED
                                                       && "after gate".equals(result.getValue())),
                   "watcher must continue receiving live results");
    }

    @Test
    void watchIsDeniedForAnotherOrganizationsRun() throws Throwable {
        CountDownLatch gateReached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition def = JobDefinition.create("gated deploy").name("gated-deploy")
            .task(Tasks.fromCallable("gate", () -> {
                gateReached.countDown();
                release.await();
                return "opened";
            }));

        JobRunHandle execution = jobService.execute(def, JobOwner.ofOrganization("acme"));
        execution.getResults().subscribeOn(Schedulers.boundedElastic()).subscribe(result -> { }, throwable -> { });
        assertTrue(gateReached.await(5, TimeUnit.SECONDS), "gate task did not start");
        try {
            Flux<Result<?>> watchFlux = onContextAs(GLOBEX_USER, () -> service.watch(execution.getJobRunId()));
            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> watchFlux.collectList().toFuture().get(10, TimeUnit.SECONDS));
            assertInstanceOf(AuthorizationException.class, unwrap(failure.getCause()));
        } finally {
            release.countDown();
        }
    }

    @Test
    void watchIsEmptyForAFinishedRun() throws Throwable {
        JobRunHandle execution = executeAndAwait(twoTaskJob("acme deploy"), JobOwner.ofOrganization("acme"));

        Flux<Result<?>> watchFlux = onContextAs(ACME_USER, () -> service.watch(execution.getJobRunId()));
        List<Result<?>> results = watchFlux.collectList().toFuture().get(10, TimeUnit.SECONDS);

        assertTrue(results.isEmpty(), "a finished run is served from its records, not a live stream");
    }

    private static JobDefinition twoTaskJob(String name) {
        return JobDefinition.create(name).name(name)
                            .task(Tasks.fromCallable("first", () -> "one"))
                            .task(Tasks.fromCallable("second", () -> "two"));
    }

    private JobRunHandle executeAndAwait(JobDefinition def, JobOwner owner) throws InterruptedException {
        JobRunHandle execution = owner != null ? jobService.execute(def, owner) : jobService.execute(def);
        CountDownLatch done = new CountDownLatch(1);
        execution.getResults().subscribe(result -> { }, throwable -> done.countDown(), done::countDown);
        assertTrue(done.await(15, TimeUnit.SECONDS), "job did not terminate within 15s");
        return execution;
    }

    /**
     * Runs the call on a Vert.x context with the given participant bound, mirroring how the
     * gateway invokes published services.
     */
    private <T> T callAs(Participant participant, Supplier<Future<T>> call) throws Throwable {
        Promise<T> result = Promise.promise();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(unused -> {
            securityContext.setParticipant(context, participant);
            try {
                call.get().onComplete(result);
            } catch (Throwable error) {
                result.fail(error);
            }
        });
        // await rethrows a failed future's raw cause, so failureOf sees the unwrapped exception
        return result.future().await(10, TimeUnit.SECONDS);
    }

    /**
     * Invokes the supplier on a Vert.x context with the given participant bound and returns
     * its immediate value, for service methods that resolve the participant at call time.
     */
    private <T> T onContextAs(Participant participant, Supplier<T> call) throws Throwable {
        CompletableFuture<T> result = new CompletableFuture<>();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(unused -> {
            securityContext.setParticipant(context, participant);
            try {
                result.complete(call.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw unwrap(e.getCause());
        }
    }

    private <T> Throwable failureOf(Participant participant, Supplier<Future<T>> call) {
        try {
            callAs(participant, call);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    /**
     * In-memory stand-in for the Elasticsearch backed {@link JobRunService}. findById
     * completes on another thread like the real service, so any SecurityContext read after
     * that hop loses the Vert.x context and fails.
     */
    private static class InMemoryJobRunService implements JobRunService {

        final Map<String, JobRun> savedJobRuns = new LinkedHashMap<>();
        final Map<String, StepRecord> savedStepRecords = new LinkedHashMap<>();

        @Override
        public Future<JobRun> save(JobRun jobRun) {
            savedJobRuns.put(jobRun.getId(), jobRun);
            return Future.succeededFuture(jobRun);
        }

        @Override
        public Future<JobRun> findById(String jobRunId) {
            // Completes on another thread like the real ES-backed service, so any
            // SecurityContext read after this hop loses the Vert.x context and fails
            Promise<JobRun> promise = Promise.promise();
            new Thread(() -> promise.complete(savedJobRuns.get(jobRunId))).start();
            return promise.future();
        }

        @Override
        public Future<Page<JobRun>> findAll(Pageable pageable) {
            List<JobRun> all = List.copyOf(savedJobRuns.values());
            return Future.succeededFuture(new Page<>(all, (long) all.size()));
        }

        @Override
        public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
            List<JobRun> matching = savedJobRuns.values().stream()
                                                .filter(run -> matchesOwner(owner, run))
                                                .toList();
            return Future.succeededFuture(new Page<>(matching, (long) matching.size()));
        }

        private static boolean matchesOwner(JobOwner owner, JobRun run) {
            boolean ret;
            if(owner.isSystem()){
                ret = run.getOrganizationId() == null;
            }else{
                ret = owner.getOrganizationId().equals(run.getOrganizationId())
                        && (owner.getApplicationId() == null || owner.getApplicationId().equals(run.getApplicationId()))
                        && (owner.getProjectId() == null || owner.getProjectId().equals(run.getProjectId()));
            }
            return ret;
        }

        @Override
        public Future<StepRecord> saveStep(StepRecord stepRecord) {
            savedStepRecords.put(stepRecord.getId(), stepRecord);
            return Future.succeededFuture(stepRecord);
        }

        @Override
        public Future<Page<StepRecord>> findSteps(String jobRunId, Pageable pageable) {
            List<StepRecord> matching = savedStepRecords.values().stream()
                                                        .filter(record -> jobRunId.equals(record.getJobRunId()))
                                                        .toList();
            int pageNumber = ((OffsetPageable) pageable).getPageNumber();
            int from = Math.min(pageNumber * pageable.getPageSize(), matching.size());
            int to = Math.min(from + pageable.getPageSize(), matching.size());
            return Future.succeededFuture(new Page<>(matching.subList(from, to), (long) matching.size()));
        }
    }

}
