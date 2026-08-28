package org.kinotic.grind;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base for grind tests: a real application context for injection, a real Vert.x instance
 * for the run threads, an in-memory ledger, and synchronous helpers over a
 * {@link JobRunHandle}.
 */
public abstract class AbstractGrindTest {

    // Instantiated before the Vertx instance below: SecurityContext registers its
    // ContextLocal in a static initializer, which Vert.x requires to happen first
    protected final SecurityContext securityContext = new SecurityContext();

    protected AnnotationConfigApplicationContext appCtx;
    protected Vertx vertx;
    protected InMemoryJobRunRepository repository;
    protected DefaultJobService jobService;

    @BeforeEach
    void setUpGrind() {
        appCtx = new AnnotationConfigApplicationContext();
        appCtx.refresh();
        vertx = Vertx.vertx();
        repository = new InMemoryJobRunRepository();
        jobService = new DefaultJobService(repository, new ObjectMapper(), vertx);
        jobService.setApplicationContext(appCtx);
    }

    @AfterEach
    void tearDownGrind() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        appCtx.close();
    }

    /**
     * Subscribes the run and blocks until it terminates, collecting every event.
     */
    protected RunResult await(JobRunHandle handle) throws InterruptedException {
        List<JobRunEvent> events = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        handle.getEvents().subscribe(events::add,
                                     throwable -> {
                                         error.set(throwable);
                                         done.countDown();
                                     },
                                     done::countDown);
        assertTrue(done.await(15, TimeUnit.SECONDS), "run did not terminate within 15s");
        return new RunResult(events, error.get());
    }

    /**
     * Polls until the condition holds, for outcomes that arrive outside the event stream,
     * such as the ledger writes after a cancellation.
     */
    protected void awaitUntil(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < deadline, "timed out waiting for: " + description);
            Thread.sleep(20);
        }
    }

}
