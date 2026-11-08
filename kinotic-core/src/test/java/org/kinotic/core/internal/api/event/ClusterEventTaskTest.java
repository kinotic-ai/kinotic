package org.kinotic.core.internal.api.event;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.ClusterEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InvalidClassException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that the event a task carries reaches the receiving node's handlers of its type as the event it was built
 * from, and that an event holding a type outside the ones a node may read is refused there.
 */
public class ClusterEventTaskTest {

    @Test
    public void handsTheEventItCarriesToTheHandlersOfItsType() {
        SampleClusterEvent event = sampleEvent("detail");

        List<ClusterEvent> handled = run(new ClusterEventTask(event));

        // each of the two sample handlers receives it once, and the revocation handler not at all
        assertEquals(List.of(event, event), handled);
    }

    @Test
    public void refusesAnEventHoldingATypeOutsideTheAllowedPackages() {
        // commons-lang3 is on every node, but a type outside the allowed packages is read by no node
        SampleClusterEvent event = sampleEvent(new ImmutablePair<>("left", "right"));
        ClusterEventTask task = new ClusterEventTask(event);

        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> run(task));

        // the filter's rejection is what the stream reports, in place of a missing class or a corrupt stream
        assertInstanceOf(InvalidClassException.class, refusal.getCause());
        assertTrue(refusal.getCause().getMessage().contains("REJECTED"),
                   "the refusal is the filter's: " + refusal.getCause().getMessage());
    }

    private static SampleClusterEvent sampleEvent(Serializable detail) {
        return new SampleClusterEvent(new SampleClusterEventKey("acme", "orders", detail),
                                      Instant.parse("2026-11-05T10:15:30Z"),
                                      Set.of("kinotic.sample"));
    }

    // what the handlers in the receiving node's Spring context are handed when the task runs there
    private static List<ClusterEvent> run(ClusterEventTask task) {
        List<ClusterEvent> handled = new ArrayList<>();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("first", SampleClusterEventHandler.class, () -> new SampleClusterEventHandler(handled));
            context.registerBean("second", SampleClusterEventHandler.class, () -> new SampleClusterEventHandler(handled));
            context.registerBean(ConnectionRevocationRecorder.class, () -> new ConnectionRevocationRecorder(handled));
            context.refresh();
            // Ignite injects the receiving node's context into the field as the task is deployed there
            ReflectionTestUtils.setField(task, "applicationContext", context);
            task.run();
        }
        return handled;
    }

}
