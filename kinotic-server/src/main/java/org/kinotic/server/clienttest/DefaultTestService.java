package org.kinotic.server.clienttest;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Vertx;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
@Component
@Profile("clienttest")
public class DefaultTestService implements ITestService{

    private static final UUID TEST_UUID = UUID.randomUUID();

    @Autowired
    private SecurityContext securityContext;
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
        return requireParticipant().getId();
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
        vertx.executeBlocking(() -> requireParticipant().getId()).onComplete(ar -> {
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
        Participant fromContext = requireParticipant();
        if (!participant.getId().equals(fromContext.getId())) {
            throw new IllegalStateException("Participant parameter ID (" + participant.getId()
                                            + ") does not match context ID (" + fromContext.getId() + ")");
        }
        if (!Objects.equals(participant.getTenantId(), fromContext.getTenantId())) {
            throw new IllegalStateException("Participant parameter tenantId does not match context tenantId");
        }
        if (!Objects.equals(participant.getRoles(), fromContext.getRoles())) {
            throw new IllegalStateException("Participant parameter roles do not match context roles");
        }
        return participant.getId();
    }

    @WithSpan
    @Override
    public Map<String, Object> getFullParticipantFromContext() {
        return participantToMap(requireParticipant());
    }

    @WithSpan
    @Override
    public Map<String, Object> getParticipantOnlyParam(Participant participant) {
        // Also verify context matches
        Participant fromContext = requireParticipant();
        if (!participant.getId().equals(fromContext.getId())) {
            throw new IllegalStateException("Participant-only param ID does not match context ID");
        }
        return participantToMap(participant);
    }

    @WithSpan
    @Override
    public CompletableFuture<String> getParticipantIdFromMonoChain() {
        return Mono.fromCallable(() -> requireParticipant().getId())
                   .map(id -> "mono:" + id)
                   .toFuture();
    }

    @WithSpan
    @Override
    public CompletableFuture<String> getParticipantIdFromNestedExecuteBlocking() {
        CompletableFuture<String> future = new CompletableFuture<>();
        vertx.executeBlocking(() -> {
            // First level: read participant
            return requireParticipant().getId();
        }).compose(firstId -> {
            // Second level: nested executeBlocking, chained non-blocking
            return vertx.executeBlocking(() -> {
                String nestedId = requireParticipant().getId();
                if (!firstId.equals(nestedId)) {
                    throw new IllegalStateException("Nested executeBlocking participant ID (" + nestedId
                                                    + ") does not match outer ID (" + firstId + ")");
                }
                return nestedId;
            });
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
    public List<String> getParticipantIdRepeated(int count) {
        List<String> ids = new ArrayList<>(count);
        String firstId = requireParticipant().getId();
        ids.add(firstId);
        for (int i = 1; i < count; i++) {
            String id = requireParticipant().getId();
            if (!firstId.equals(id)) {
                throw new IllegalStateException("Participant ID changed during invocation at iteration " + i
                                                + ": expected " + firstId + " got " + id);
            }
            ids.add(id);
        }
        return ids;
    }

    @WithSpan
    @Override
    public String participantFirstArgWithContext(Participant participant, String suffix) {
        Participant fromContext = requireParticipant();
        if (!participant.getId().equals(fromContext.getId())) {
            throw new IllegalStateException("First-arg participant ID does not match context ID");
        }
        return participant.getId() + suffix;
    }

    @WithSpan
    @Override
    public String participantLastArgWithContext(String prefix, Participant participant) {
        Participant fromContext = requireParticipant();
        if (!participant.getId().equals(fromContext.getId())) {
            throw new IllegalStateException("Last-arg participant ID does not match context ID");
        }
        return prefix + participant.getId();
    }

    @WithSpan
    @Override
    public CompletableFuture<String> verifyParticipantInMonoChain(Participant participant) {
        return Mono.fromCallable(() -> {
            Participant fromContext = requireParticipant();
            if (!participant.getId().equals(fromContext.getId())) {
                throw new IllegalStateException("Mono chain: param ID (" + participant.getId()
                                                + ") does not match context ID (" + fromContext.getId() + ")");
            }
            if (!Objects.equals(participant.getRoles(), fromContext.getRoles())) {
                throw new IllegalStateException("Mono chain: param roles do not match context roles");
            }
            return fromContext.getId();
        }).toFuture();
    }

    private String internalGetParticipantId() {
        return requireParticipant().getId();
    }

    private Participant requireParticipant() {
        Participant participant = securityContext.currentParticipant();
        if (participant == null) {
            throw new IllegalStateException("No Participant in Vert.x context");
        }
        return participant;
    }

    private Map<String, Object> participantToMap(Participant participant) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", participant.getId());
        result.put("tenantId", participant.getTenantId());
        result.put("roles", participant.getRoles());
        result.put("metadata", participant.getMetadata());
        return result;
    }

}
