package org.kinotic.server.clienttest;


import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;

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

}
