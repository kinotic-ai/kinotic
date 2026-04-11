package org.kinotic.server.clienttest;


import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.security.Participant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
@Publish
@Version("1.0.0")
public interface ITestService {

    String testMethodWithString(String value);

    UUID getTestUUID();

    /**
     * Returns the Participant ID from the Vert.x context directly
     */
    String getParticipantIdFromContext();

    /**
     * Delegates to an internal method that reads Participant from context
     */
    String getParticipantIdFromContextViaDispatch();

    /**
     * Reads Participant from context inside vertx.executeBlocking()
     */
    CompletableFuture<String> getParticipantIdFromContextInExecuteBlocking();

    /**
     * Takes a Participant as a method parameter and also reads from context, verifies they match
     */
    String verifyParticipantParameterMatchesContext(Participant participant);

    /**
     * Returns a map of all Participant fields from the context (id, tenantId, roles, metadata)
     */
    Map<String, Object> getFullParticipantFromContext();

    /**
     * Method with only a Participant parameter, returns the participant's info.
     * Exercises the zero-JSON-args code path.
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
     * Reads the Participant N times in a loop to verify consistency within a single invocation
     */
    List<String> getParticipantIdRepeated(int count);

    /**
     * Participant as first arg with a suffix, verifies context matches param
     */
    String participantFirstArgWithContext(Participant participant, String suffix);

    /**
     * Participant as last arg with a prefix, verifies context matches param
     */
    String participantLastArgWithContext(String prefix, Participant participant);

}
