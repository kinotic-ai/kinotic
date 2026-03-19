package org.kinotic.persistence.internal.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.security.Participant;
import org.kinotic.persistence.api.model.EntityContext;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 6/8/23.
 */
@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class DefaultEntityContext implements EntityContext {

    private Participant participant;

    private List<String> includedFieldsFilter = null;

    private List<String> tenantSelection;

    public DefaultEntityContext(Participant participant,
                                List<String> includedFieldsFilter) {
        this.participant = participant;
        this.includedFieldsFilter = includedFieldsFilter;
    }

    public DefaultEntityContext(Participant participant) {
        this.participant = participant;
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
