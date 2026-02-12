

package org.kinotic.rpc.api.security;


import java.util.concurrent.CompletableFuture;

/**
 * Provides an abstraction for creating and accessing {@link Session}'s
 *
 * Created by navid on 1/23/20
 */
public interface SessionManager {

    /**
     * Create a new {@link Session} for the given {@link DefaultParticipant}
     * @param participant the {@link DefaultParticipant} to create the {@link Session} for
     * @param replyToId that was provided by the client
     * @return a {@link CompletableFuture} containing the new {@link Session} or an error if the {@link Session} cannot be created
     */
    CompletableFuture<Session> create(Participant participant, String replyToId);

    /**
     * Removes the {@link Session} from the internal known sessions.
     * @param sessionId the id of the {@link Session} to remove
     * @return a {@link CompletableFuture} containing the result of the removal operation.
     *         True if there was a session for the given sessionId or false if there was no session for the id.
     */
    CompletableFuture<Boolean> removeSession(String sessionId);

    /**
     * Finds a previously created {@link Session} by the sessionId
     * @param sessionId to find the {@link Session} for
     * @return a {@link CompletableFuture} containing the existing {@link Session} or an error if the {@link Session} cannot be found
     */
    CompletableFuture<Session> findSession(String sessionId);

}
