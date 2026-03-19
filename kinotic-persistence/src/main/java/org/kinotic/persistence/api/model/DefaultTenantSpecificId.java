package org.kinotic.persistence.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created By Navíd Mitchell 🤪on 2/20/25
 */
public record DefaultTenantSpecificId(@JsonProperty("entityId") String entityId,
                                      @JsonProperty("tenantId") String tenantId) implements TenantSpecificId {

}
