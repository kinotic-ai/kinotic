package org.kinotic.client.internal.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.kinotic.core.api.security.Participant;

import java.util.List;
import java.util.Map;

/**
 * The participant the gateway authenticated a connection as, read from the CONNECTED frame: the identity, roles and
 * metadata every participant has, whatever scope the gateway's own model gives it.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Accessors(chain = true)
public class ConnectedParticipant implements Participant {

    private String id;
    private Map<String, String> metadata;
    private List<String> roles;

}
