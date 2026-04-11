package org.kinotic.server.clienttest;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Vertx;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
@Component
@Profile("clienttest")
public class DefaultTestService implements ITestService{

    private static final UUID TEST_UUID = UUID.randomUUID();

    @Autowired
    private ParticipantContext participantContext;
    @Autowired
    private Vertx vertx;

    @WithSpan
    @Override
    public String testMethodWithString(String value) {
        return "Hello "+ value;
    }

    @WithSpan
    @Override
    public UUID getTestUUID(){
        return TEST_UUID;
    }

    @WithSpan
    @Override
    public String getParticipantIdFromContext() {
        Participant participant = participantContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant in Vert.x context");
        }
        return participant.getId();
    }

    @WithSpan
    @Override
    public String getParticipantIdFromContextViaDispatch() {
        return internalGetParticipantId();
    }

    @WithSpan
    @Override
    public CompletableFuture<String> getParticipantIdFromContextInExecuteBlocking() {
        CompletableFuture<String> future = new CompletableFuture<>();
        vertx.<String>executeBlocking(() -> {
            Participant participant = participantContext.currentParticipant();
            if (participant == null) {
                throw new IllegalStateException("No Participant in Vert.x context in executeBlocking");
            }
            return participant.getId();
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                future.complete(ar.result());
            } else {
                future.completeExceptionally(ar.cause());
            }
        });
        return future;
    }

    @WithSpan
    @Override
    public String verifyParticipantParameterMatchesContext(Participant participant) {
        Participant fromContext = participantContext.currentParticipant();
        if (fromContext == null) {
            throw new IllegalStateException("No Participant in Vert.x context");
        }
        if (!participant.getId().equals(fromContext.getId())) {
            throw new IllegalStateException("Participant parameter ID (" + participant.getId()
                                            + ") does not match context ID (" + fromContext.getId() + ")");
        }
        return participant.getId();
    }

    @WithSpan
    @Override
    public Map<String, Object> getFullParticipantFromContext() {
        Participant participant = participantContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant in Vert.x context");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", participant.getId());
        result.put("tenantId", participant.getTenantId());
        result.put("roles", participant.getRoles());
        result.put("metadata", participant.getMetadata());
        return result;
    }

    private String internalGetParticipantId() {
        Participant participant = participantContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant in Vert.x context");
        }
        return participant.getId();
    }

}
