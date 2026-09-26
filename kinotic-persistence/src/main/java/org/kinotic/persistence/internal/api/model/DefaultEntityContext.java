package org.kinotic.persistence.internal.api.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.persistence.api.model.EntityContext;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 6/8/23.
 */
@Getter
@Accessors(chain = true)
public class DefaultEntityContext implements EntityContext {

    private final ScopedParticipant participant;

    private final String organizationId;

    private final String tenantId;

    @Setter
    private List<String> includedFieldsFilter;

    @Setter
    private List<String> tenantSelection;

    public DefaultEntityContext(ScopedParticipant participant) {
        this(participant, null);
    }

    public DefaultEntityContext(ScopedParticipant participant,
                                List<String> includedFieldsFilter) {
        ParticipantScope scope = participant.getScope();
        // A SystemParticipant may address app-api but carries no organization, and every entity
        // index is addressed by organization; rejecting it here names the reason
        Validate.notBlank(scope.organizationId(),
                          "Entity data belongs to an Organization, and participant %s carries none",
                          participant.getId());
        this.participant = participant;
        this.organizationId = scope.organizationId();
        this.tenantId = scope.tenantId();
        this.includedFieldsFilter = includedFieldsFilter;
    }

    @Override
    public boolean hasIncludedFieldsFilter() {
        return includedFieldsFilter != null;
    }

    @Override
    public boolean hasTenantSelection() {
        return tenantSelection != null && !tenantSelection.isEmpty();
    }

}
