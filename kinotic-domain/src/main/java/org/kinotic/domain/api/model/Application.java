package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Application implements OrganizationScoped<String> {

    private String id;

    private String organizationId;

    private String description;

    private List<String> oidcConfigurationIds;

    /**
     * When true, every APPLICATION-scope {@link org.kinotic.domain.api.model.iam.IamUser}
     * created for this application receives an auto-generated unique {@code tenantId},
     * isolating each user's {@code MultiTenancyType.SHARED} entity data in its own tenant.
     * Applies only to users created after it is enabled; existing users are not backfilled.
     */
    private boolean tenantPerUser = false;

    private Date updated = null;

    public Application(String id, String description) {
        this.id = id;
        this.description = description;
    }

}
