package org.mindignited.structures.internal.endpoints.openapi;

import io.vertx.ext.web.RoutingContext;
import org.mindignited.continuum.api.security.Participant;
import org.mindignited.continuum.core.api.event.EventConstants;
import org.mindignited.structures.api.domain.EntityContext;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
public class RoutingContextToEntityContextAdapter implements EntityContext {

    private final RoutingContext routingContext;
    private List<String> tenantSelection;

    public RoutingContextToEntityContextAdapter(RoutingContext routingContext) {
        this.routingContext = routingContext;
    }


    @Override
    public List<String> getIncludedFieldsFilter() {
        return null;
    }

    @Override
    public Participant getParticipant() {
        return routingContext.get(EventConstants.SENDER_HEADER);
    }

    @Override
    public boolean hasIncludedFieldsFilter() {
        return false;
    }

    @Override
    public boolean hasTenantSelection() {
        return tenantSelection != null && !tenantSelection.isEmpty();
    }

    @Override
    public List<String> getTenantSelection() {
        return tenantSelection;
    }

    @Override
    public RoutingContextToEntityContextAdapter setTenantSelection(List<String> tenantSelection) {
        this.tenantSelection = tenantSelection;
        return this;
    }

}
