package org.kinotic.core.internal.api.event;

import org.kinotic.core.api.security.Participant;

import java.util.Map;

/**
 * Wire representation of an {@link org.kinotic.core.api.event.Event}'s envelope (everything except the
 * raw payload bytes), serialized by {@link EventMessageCodec} for remote delivery. The {@code sender}
 * is (de)serialized polymorphically via the {@code Participant} mixin configured on the shared mapper.
 */
record EventEnvelope(String cri, Participant sender, Map<String, String> metadata) {
}
