package org.kinotic.server.clienttest;


import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.utils.DomainUtil;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.OS_API_ZONE)
public interface ITestService {

    String testMethodWithString(String value);

    UUID getTestUUID();

    /**
     * Returns a fixed binary payload to exercise the {@code application/octet-stream} passthrough.
     */
    byte[] getBinaryData();

    /**
     * Streams fixed binary chunks to exercise per-element {@code application/octet-stream} passthrough.
     */
    Flux<byte[]> getBinaryDataStream();

    /**
     * Returns the Participant ID from the Vert.x context directly
     */
    String getParticipantIdFromContext();


    /**
     * Reads Participant from context inside vertx.executeBlocking()
     */
    CompletableFuture<String> getParticipantIdFromContextInExecuteBlocking();


    /**
     * Returns a map of all Participant fields from the context (id, tenantId, roles, metadata)
     */
    Map<String, Object> getFullParticipantFromContext();

    /**
     * Takes a Participant as its only parameter; the caller sends no arguments, so the invoker
     * must supply it. Returns the injected Participant's fields (id, tenantId, roles, metadata).
     */
    Map<String, Object> getParticipantOnlyParam(Participant participant);


    /**
     * Reads Participant inside a Mono reactive chain
     */
    CompletableFuture<String> getParticipantIdFromMonoChain();

    /**
     * Reads Participant inside a nested vertx.executeBlocking() call
     */
    CompletableFuture<String> getParticipantIdFromNestedExecuteBlocking();


    /**
     * Participant as first arg with a suffix, verifies context matches param
     */
    String participantFirstArgWithContext(Participant participant, String suffix);

    /**
     * Participant as last arg with a prefix, verifies context matches param
     */
    String participantLastArgWithContext(String prefix, Participant participant);

    /**
     * Takes Participant as a param and reads from context inside a Mono chain, verifies they match
     */
    CompletableFuture<String> verifyParticipantInMonoChain(Participant participant);

}
