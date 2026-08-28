package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire publication is its own axis, independent of the {@code StoreType}: only values whose
 * {@link Store} declares {@code wire()} reach the event's and record's JSON form, whatever
 * their resume behavior.
 */
public class WireTest extends AbstractGrindTest {

    @Test
    public void wiredResultReachesTheEventButNotTheRecord() throws Exception {
        JobDefinition job = JobDefinition.create("wired result")
                .name("wired-result").version("1")
                .task(Tasks.fromValue("allocate widget", new Widget("wl-123")),
                      Store.result("widget").wire());

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        TaskCompletedEvent completed = completionAt(result, "0/1");
        assertEquals(new Widget("wl-123"), completed.storedValue());
        assertNotNull(completed.wireValue());
        assertEquals("wl-123", completed.wireValue().get("name").stringValue());

        // published, not persisted: the record carries no state columns for a RESULT store
        TaskRecord record = repository.taskAt(handle.getJobRunId(), "0/1");
        assertNull(record.getStateValue());
        assertNull(record.getStateValueType());
    }

    @Test
    public void unwiredValuesStayOffTheWire() throws Exception {
        JobDefinition job = JobDefinition.create("dark")
                .name("dark").version("1")
                .task(Tasks.fromValue("produce widget", new Widget("private")), Store.result("widget"))
                .task(Tasks.fromValue("decide", new WidgetState("secret")), Store.state("widgetState"));

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertNull(completionAt(result, "0/1").wireValue());
        assertNull(completionAt(result, "0/2").wireValue());
        // durable but not published: STATE keeps its replay columns without reaching the wire
        assertNotNull(repository.taskAt(handle.getJobRunId(), "0/2").getStateValue());
    }

    @Test
    public void wiredStatePublishesItsDurableForm() throws Exception {
        JobDefinition job = JobDefinition.create("wired state")
                .name("wired-state").version("1")
                .task(Tasks.fromValue("decide", new WidgetState("shown")),
                      Store.state("widgetState").wire());

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        assertEquals("shown", completionAt(result, "0/1").wireValue().get("name").stringValue());
        assertNotNull(repository.taskAt(handle.getJobRunId(), "0/1").getStateValue());
    }

    @Test
    public void wireOnlyValueIsPublishedWithoutEnteringTheScope() throws Exception {
        AtomicReference<Widget> inScope = new AtomicReference<>(new Widget("sentinel"));
        JobDefinition job = JobDefinition.create("wire only")
                .name("wire-only").version("1")
                .task(Tasks.fromValue("observe widget", new Widget("observed")), Store.none().wire())
                .task(new org.kinotic.grind.api.model.Task<Void>() {
                    @Override
                    public String getDescription() {
                        return "probe scope";
                    }

                    @Override
                    public Void execute(JobContext context) {
                        inScope.set(context.getBeanOrNull(Widget.class));
                        return null;
                    }
                });

        JobRunHandle handle = jobService.run(job, JobOwner.system());
        RunResult result = await(handle);

        assertNull(result.error());
        TaskCompletedEvent completed = completionAt(result, "0/1");
        assertEquals(StoreType.NONE, completed.storeType());
        assertNull(completed.storedName());
        assertEquals("observed", completed.wireValue().get("name").stringValue());
        assertNull(inScope.get());

        TaskRecord record = repository.taskAt(handle.getJobRunId(), "0/1");
        assertNull(record.getStoredName());
        assertNull(record.getStateValue());
    }

    @Test
    public void taskAnnotationCanDeclareAWireOnlyValue() throws Exception {
        JobDefinition job = JobDefinition.fromTasks(WireTasks.class).name("wire-tasks").version("1");

        RunResult result = await(jobService.run(job, JobOwner.system()));

        assertNull(result.error());
        TaskCompletedEvent completed = completionAt(result, "0/1");
        assertEquals(StoreType.NONE, completed.storeType());
        assertEquals("observed", completed.wireValue().get("name").stringValue());
    }

    @Test
    public void wireAcceptsGenericValuesThatStateRejects() throws Exception {
        JobDefinition job = JobDefinition.create("generic wire")
                .name("generic-wire").version("1")
                .task(Tasks.fromValue("collect labels", new ArrayList<>(List.of("a", "b"))),
                      Store.result("labels").wire());

        RunResult result = await(jobService.run(job, JobOwner.system()));

        // the wire form is rendered, never restored, so erasure does not matter
        assertNull(result.error());
        assertEquals(2, completionAt(result, "0/1").wireValue().size());
    }

    @Test
    public void unserializableWireValueFailsTheTask() throws Exception {
        JobDefinition job = JobDefinition.create("bad wire")
                .name("bad-wire").version("1")
                .task(Tasks.fromValue("produce cycle", new Cyclic()), Store.result("cycle").wire());

        RunResult result = await(jobService.run(job, JobOwner.system()));

        IllegalStateException error = assertInstanceOf(IllegalStateException.class, result.error());
        assertTrue(error.getMessage().contains("declared wire"));
    }

    private TaskCompletedEvent completionAt(RunResult result, String taskPath) {
        return result.events().stream()
                     .filter(TaskCompletedEvent.class::isInstance)
                     .map(TaskCompletedEvent.class::cast)
                     .filter(event -> event.taskPath().equals(taskPath))
                     .findFirst().orElseThrow();
    }

    /**
     * Self-referencing value no JSON serializer can render.
     */
    static class Cyclic {
        public Cyclic self = this;
    }

}
