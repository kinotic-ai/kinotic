package org.kinotic.grindv2;

import org.kinotic.core.api.security.Participant;

import java.util.List;
import java.util.Map;

/**
 * A minimal {@link Participant} bound to run contexts in tests.
 *
 * @param id the participant identity
 */
public record TestParticipant(String id) implements Participant {

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, String> getMetadata() {
        return Map.of();
    }

    @Override
    public List<String> getRoles() {
        return List.of();
    }

}
