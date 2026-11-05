package org.kinotic.core.internal.api.event;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerType;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins that the event a task carries reaches a node's Spring context as the event it was built from, and that an
 * event holding a type outside the ones a node may read is refused there.
 */
public class ClusterEventTaskTest {

    @Test
    public void publishesTheEventItCarriesOnTheReceivingNode() {
        SampleClusterEvent event = sampleEvent("detail");

        List<Object> published = run(new ClusterEventTask(event));

        assertEquals(List.of(event), published);
    }

    @Test
    public void refusesAnEventHoldingATypeOutsideTheAllowedPackages() {
        // commons-lang3 is on every node, but a type outside the allowed packages is read by no node
        SampleClusterEvent event = sampleEvent(new ImmutablePair<>("left", "right"));
        ClusterEventTask task = new ClusterEventTask(event);

        assertThrows(IllegalStateException.class, () -> run(task));
    }

    private static SampleClusterEvent sampleEvent(Serializable detail) {
        return new SampleClusterEvent(new SampleClusterEventKey("acme", "orders", detail),
                                      Instant.parse("2026-11-05T10:15:30Z"),
                                      Set.of(ServerType.APP, ServerType.SYSTEM));
    }

    // what the task publishes into a node's Spring context when it runs there
    private static List<Object> run(ClusterEventTask task) {
        List<Object> published = new ArrayList<>();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            context.addApplicationListener(applicationEvent -> {
                if (applicationEvent instanceof PayloadApplicationEvent<?> payload) {
                    published.add(payload.getPayload());
                }
            });
            // Ignite injects the receiving node's context into the field as the task is deployed there
            ReflectionTestUtils.setField(task, "applicationContext", context);
            task.run();
        }
        return published;
    }

}
