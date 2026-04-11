package org.kinotic.server.clienttest;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.internal.config.KinoticVertxConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
        Context context = Vertx.currentContext();
        if (context == null) {
            throw new IllegalStateException("No Vert.x context available");
        }
        Participant participant = context.getLocal(KinoticVertxConfig.PARTICIPANT_LOCAL);
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
            Context context = Vertx.currentContext();
            if (context == null) {
                throw new IllegalStateException("No Vert.x context available in executeBlocking");
            }
            Participant participant = context.getLocal(KinoticVertxConfig.PARTICIPANT_LOCAL);
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

    private String internalGetParticipantId() {
        Context context = Vertx.currentContext();
        if (context == null) {
            throw new IllegalStateException("No Vert.x context available");
        }
        Participant participant = context.getLocal(KinoticVertxConfig.PARTICIPANT_LOCAL);
        if (participant == null) {
            throw new IllegalStateException("No Participant in Vert.x context");
        }
        return participant.getId();
    }

}
