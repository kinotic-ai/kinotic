package org.kinotic.core.internal.api.event;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerType;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins that the event a task carries reaches a node's Spring context as the event it was built from.
 */
public class ClusterEventTaskTest {

    @Test
    public void publishesTheEventItCarriesOnTheReceivingNode() {
        SampleClusterEvent event = new SampleClusterEvent(new SampleClusterEvent.Key("acme", "orders"),
                                                          Instant.parse("2026-11-05T10:15:30Z"),
                                                          Set.of(ServerType.APP, ServerType.SYSTEM));
        List<Object> published = new ArrayList<>();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            context.addApplicationListener(applicationEvent -> {
                if (applicationEvent instanceof PayloadApplicationEvent<?> payload) {
                    published.add(payload.getPayload());
                }
            });
            ClusterEventTask task = new ClusterEventTask(event);
            // Ignite injects the receiving node's context into the field as the task is deployed there
            ReflectionTestUtils.setField(task, "applicationContext", context);

            task.run();
        }

        assertEquals(List.of(event), published);
    }

}
