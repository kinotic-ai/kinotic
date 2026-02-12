

package org.kinotic.rpc.api.security;

import org.kinotic.rpc.api.event.CRI;

import java.util.Date;

/**
 *
 * Created by navid on 1/23/20
 */
public interface Session {

    Participant participant();

    String sessionId();

    String replyToId();

    Date lastUsedDate();

    /**
     * Updates the lastUsedDate to the current date and time
     */
    void touch();

    /**
     * Adds a criPattern that will allow a send to the given {@link CRI} one time
     * This is determined by a call to {@link Session#sendAllowed(CRI)} after the first call returning true the criPattern will not be allowed again
     * @param criPattern to add to the temporary allowed list
     */
    void addTemporarySendAllowed(String criPattern);

    boolean sendAllowed(CRI cri);

    boolean subscribeAllowed(CRI cri);

}
