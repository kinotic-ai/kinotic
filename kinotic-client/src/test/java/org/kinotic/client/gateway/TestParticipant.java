package org.kinotic.client.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.kinotic.core.api.security.Participant;

import java.util.List;
import java.util.Map;

/**
 * The participant the test gateway authenticates a connection as.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@AllArgsConstructor
public class TestParticipant implements Participant {

    private final String id;
    private final Map<String, String> metadata;
    private final List<String> roles;

}
