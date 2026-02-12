

package org.kinotic.rpc.api.security;

import java.util.Date;

/**
 *
 * Created by Navid Mitchell on 6/4/20
 */
public interface SessionMetadata {

    String getSessionId();

    String getReplyToId();

    Participant getParticipant();

    Date getLastUsedDate();

}
