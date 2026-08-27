package org.kinotic.grindv2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.kinotic.grindv2.api.JobRunEvent;
import org.kinotic.grindv2.api.JobRunHandle;
import org.kinotic.grindv2.internal.DefaultJobRunner;
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
 * Base for grindv2 tests: a real application context for injection, an in-memory ledger, and
 * synchronous helpers over a {@link JobRunHandle}.
 */
public abstract class AbstractGrindV2Test {

    protected AnnotationConfigApplicationContext appCtx;
    protected InMemoryJobRunRepository repository;
    protected DefaultJobRunner jobRunner;

    @BeforeEach
    void setUpGrindV2() {
        appCtx = new AnnotationConfigApplicationContext();
        appCtx.refresh();
        repository = new InMemoryJobRunRepository();
        jobRunner = new DefaultJobRunner(repository, new ObjectMapper());
        jobRunner.setApplicationContext(appCtx);
    }

    @AfterEach
    void tearDownGrindV2() {
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
