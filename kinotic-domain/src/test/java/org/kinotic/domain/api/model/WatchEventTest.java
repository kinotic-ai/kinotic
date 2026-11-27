package org.kinotic.domain.api.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WatchEventTest {

    @Test
    public void carriesTheDataStreamTimeFieldUnderItsRequiredName() {
        JsonMapper mapper = JsonMapper.builder().build();
        WatchEvent event = new WatchEvent(new Date(), WatchedType.WORKLOAD, "w1", "web", "org-1",
                                          new WatchedParent(WatchedType.MICROSERVICE_DEPLOYMENT, "org-1", "svc-a"),
                                          WatchEventType.STATUS_CHANGED, "node n1", "server-1", null,
                                          "Run STOPPED", mapper.valueToTree(Map.of("status", "STOPPED")));

        String json = mapper.writeValueAsString(event);
        WatchEvent read = mapper.readValue(json, WatchEvent.class);

        assertTrue(json.contains("\"@timestamp\""), json);
        assertEquals(event.timestamp(), read.timestamp());
        assertEquals(event.parent(), read.parent());
        assertEquals(event.eventType(), read.eventType());
        assertEquals(mapper.valueToTree(Map.of("status", "STOPPED")), read.value());
    }
}
