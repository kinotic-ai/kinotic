package org.kinotic.domain.api.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WatchedParentTest {

    @Test
    public void rendersAsOneStringAndParsesBackWhateverTheIdHolds() {
        WatchedParent parent = new WatchedParent(WatchedType.MICROSERVICE_DEPLOYMENT, "org-1", "proj-9:api");

        assertEquals("MICROSERVICE_DEPLOYMENT:org-1:proj-9:api", parent.value());
        assertEquals(parent, WatchedParent.parse(parent.value()));
    }

    @Test
    public void serializesAsTheOneStringOnTheWire() {
        JsonMapper mapper = JsonMapper.builder().build();
        WatchedState state = new WatchedState().setParent(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, "org-1", "proj-9"));

        String json = mapper.writeValueAsString(state);
        WatchedState read = mapper.readValue(json, WatchedState.class);

        assertEquals("\"PROJECT_DEPLOYMENT:org-1:proj-9\"", mapper.writeValueAsString(state.getParent()));
        assertEquals(state.getParent(), read.getParent());
    }

    @Test
    public void aParentWithoutScopeRendersAnEmptyScopeAndParsesBack() {
        WatchedParent parent = new WatchedParent(WatchedType.AUTHZ_STORE, null, "platform");
        assertEquals("AUTHZ_STORE::platform", parent.value());
        assertEquals(parent, WatchedParent.parse(parent.value()));
        assertEquals("AUTHZ_STORE::", WatchedParent.valuePrefix(WatchedType.AUTHZ_STORE, null));
    }

    @Test
    public void refusesAValueWithoutTypeOrId() {
        assertThrows(IllegalArgumentException.class, () -> WatchedParent.parse("proj-9"));
        assertThrows(IllegalArgumentException.class, () -> WatchedParent.parse("WORKLOAD:proj-9"));
        assertThrows(IllegalArgumentException.class, () -> WatchedParent.parse("WORKLOAD:org-1:"));
        assertThrows(IllegalArgumentException.class, () -> new WatchedParent(WatchedType.WORKLOAD, " ", "proj-9"));
    }
}
