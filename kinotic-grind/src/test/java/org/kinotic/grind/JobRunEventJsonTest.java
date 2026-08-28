package org.kinotic.grind;

import org.junit.jupiter.api.Test;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.events.TaskFailedEvent;
import org.kinotic.grind.api.model.events.TaskProgressEvent;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import org.kinotic.grind.api.model.events.TasksDiscoveredEvent;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The wire form of the event family: every event names its concrete type so a remote watcher
 * can deserialize the stream back into {@link JobRunEvent}.
 */
public class JobRunEventJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void everyEventCarriesItsTypeDiscriminator() {
        assertEquals("tasksDiscovered", typeOf(new TasksDiscoveredEvent("0", List.of())));
        assertEquals("taskStarted", typeOf(new TaskStartedEvent("0/1", "work")));
        assertEquals("taskProgress", typeOf(new TaskProgressEvent("0/1", 40, "halfway")));
        assertEquals("taskCompleted", typeOf(new TaskCompletedEvent("0/1", StoreType.NONE, null, null, null)));
        assertEquals("taskFailed", typeOf(new TaskFailedEvent("0/1", "boom")));
    }

    @Test
    public void aCompletionRoundTripsThroughItsWireValue() {
        TaskCompletedEvent completed = new TaskCompletedEvent("0/3", StoreType.RESULT, "runtimeWorkloadId",
                                                              null, StringNode.valueOf("wl-8f21"));

        JobRunEvent restored = objectMapper.readValue(objectMapper.writeValueAsString(completed), JobRunEvent.class);

        TaskCompletedEvent event = assertInstanceOf(TaskCompletedEvent.class, restored);
        assertEquals("0/3", event.taskPath());
        assertEquals(StoreType.RESULT, event.storeType());
        assertEquals("runtimeWorkloadId", event.storedName());
        assertEquals("wl-8f21", event.wireValue().stringValue());
        assertNull(event.storedValue());
    }

    @Test
    public void aDiscoveryRoundTripsItsPendingRecords() {
        TaskRecord record = new TaskRecord().setId("run:0/1")
                                            .setJobRunId("run")
                                            .setTaskPath("0/1")
                                            .setDescription("inner")
                                            .setStatus(ExecutionStatus.PENDING);

        JobRunEvent restored = objectMapper.readValue(
                objectMapper.writeValueAsString(new TasksDiscoveredEvent("0", List.of(record))), JobRunEvent.class);

        TasksDiscoveredEvent event = assertInstanceOf(TasksDiscoveredEvent.class, restored);
        assertEquals("0", event.taskPath());
        assertEquals(List.of("0/1"), event.tasks().stream().map(TaskRecord::getTaskPath).toList());
        assertEquals(ExecutionStatus.PENDING, event.tasks().getFirst().getStatus());
    }

    private String typeOf(JobRunEvent event) {
        return objectMapper.readTree(objectMapper.writeValueAsString(event)).get("type").stringValue();
    }

}
