package org.kinotic.persistence.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A object to hold a query and a list of tenants that the query should be executed against
 * Created By Navíd Mitchell 🤪on 2/20/25
 */
public record QueryWithTenantSelection(@JsonProperty("query") String query,
                                       @JsonProperty("tenantSelection") List<String> tenantSelection) {
}
