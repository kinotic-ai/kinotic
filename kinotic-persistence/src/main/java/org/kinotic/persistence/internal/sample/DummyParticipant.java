package org.kinotic.persistence.internal.sample;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.rpc.api.security.ParticipantConstants;
import org.kinotic.rpc.api.security.Participant;

import java.util.List;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪on 6/18/23.
 */
@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class DummyParticipant implements Participant {

    private String tenantId = "kinotic";
    private String id = "dummy";
    private Map<String, String> metadata = Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                  ParticipantConstants.PARTICIPANT_TYPE_USER);
    private List<String> roles = List.of("DUMMY");

    public DummyParticipant(String tenantId, String id) {
        this.tenantId = tenantId;
        this.id = id;
    }
}
