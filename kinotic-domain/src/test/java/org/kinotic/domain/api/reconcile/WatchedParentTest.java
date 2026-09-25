package org.kinotic.domain.api.reconcile;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WatchedParentTest {

    @Test
    public void rendersAsOneStringAndParsesBackWhateverTheIdHolds() {
        WatchedParent parent = new WatchedParent(WatchedType.MICROSERVICE_DEPLOYMENT, "proj-9:api");

        assertEquals("MICROSERVICE_DEPLOYMENT:proj-9:api", parent.value());
        assertEquals(parent, WatchedParent.parse(parent.value()));
    }

    @Test
    public void serializesAsTheOneStringOnTheWire() {
        JsonMapper mapper = JsonMapper.builder().build();
        WatchedState state = new WatchedState().setParent(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, "proj-9"));

        String json = mapper.writeValueAsString(state);
        WatchedState read = mapper.readValue(json, WatchedState.class);

        assertEquals("\"PROJECT_DEPLOYMENT:proj-9\"", mapper.writeValueAsString(state.getParent()));
        assertEquals(state.getParent(), read.getParent());
    }

    @Test
    public void refusesAValueWithNoType() {
        assertThrows(IllegalArgumentException.class, () -> WatchedParent.parse("proj-9"));
        assertThrows(IllegalArgumentException.class, () -> WatchedParent.parse("WORKLOAD:"));
    }
}
