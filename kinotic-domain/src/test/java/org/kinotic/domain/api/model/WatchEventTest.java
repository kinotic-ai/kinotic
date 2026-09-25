package org.kinotic.domain.api.model;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.reconcile.WatchedParent;
import org.kinotic.core.api.reconcile.WatchedType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WatchEventTest {

    @Test
    public void carriesTheDataStreamTimeFieldUnderItsRequiredName() {
        JsonMapper mapper = JsonMapper.builder().build();
        WatchEvent event = new WatchEvent(new Date(), WatchedType.WORKLOAD, "w1",
                                          new WatchedParent(WatchedType.MICROSERVICE_DEPLOYMENT, "svc-a"),
                                          WatchEventKind.STATUS_CHANGED, "node n1", "server-1", null,
                                          "Run STOPPED", Map.of("status", "STOPPED"));

        String json = mapper.writeValueAsString(event);
        WatchEvent read = mapper.readValue(json, WatchEvent.class);

        assertTrue(json.contains("\"@timestamp\""), json);
        assertEquals(event.timestamp(), read.timestamp());
        assertEquals(event.parent(), read.parent());
        assertEquals(event.kind(), read.kind());
        assertEquals(Map.of("status", "STOPPED"), read.value());
    }
}
