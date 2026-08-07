package org.kinotic.orchestrator.internal.api.grind;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.kinotic.orchestrator.api.model.grind.JobExecution;
import org.kinotic.orchestrator.api.model.grind.ResultType;
import org.kinotic.orchestrator.internal.api.model.grind.DefaultJobService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base for grind tests: a real application context for injection, in-memory record services,
 * and a synchronous await over a {@link JobExecution}.
 */
public abstract class AbstractGrindTest {

    protected AnnotationConfigApplicationContext appCtx;
    protected StubJobRunService runs;
    protected StubTaskRecordService records;
    protected ObjectMapper objectMapper;
    protected DefaultJobService jobService;

    @BeforeEach
    void setUpGrind() {
        appCtx = new AnnotationConfigApplicationContext();
        appCtx.refresh();
        runs = new StubJobRunService();
        records = new StubTaskRecordService();
        objectMapper = new ObjectMapper();
        jobService = new DefaultJobService(runs, records, objectMapper);
        jobService.setApplicationContext(appCtx);
    }

    @AfterEach
    void tearDownGrind() {
        appCtx.close();
    }

    /**
     * Subscribes the execution and blocks until it terminates, collecting VALUE results.
     */
    protected RunOutcome await(JobExecution execution) throws InterruptedException {
        List<Object> values = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        execution.getResults().subscribe(
            result -> {
                if(result.getResultType() == ResultType.VALUE){
                    values.add(result.getValue());
                }
            },
            throwable -> {
                error.set(throwable);
                done.countDown();
            },
            done::countDown);
        assertTrue(done.await(15, TimeUnit.SECONDS), "job did not terminate within 15s");
        return new RunOutcome(values, error.get());
    }

}
