package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Date;

/**
 * A tenant of an application whose tenants its administrators manage ({@link TenantManagement#TENANT_ADMINISTRATORS}):
 * a customer of the application, whose users carry its id as their {@code tenantId} and whose rows are isolated
 * under it. A tenant has a login of its own: its sites, {@code <tenantId>--<applicationId>--<uiName>} under the
 * organization's subdomain of the sites domain, sign its users in, and may offer providers the tenant configured
 * ({@code TenantOidcConfiguration}). An application whose tenants the platform manages gives each user a tenant
 * at creation and has no rows here.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Tenant implements ApplicationScoped<String> {

    /**
     * The tenant's id, the slugified {@link #name} minted at creation: lowercase letters, digits and single
     * interior dashes, since it is the first name of the tenant's site labels. The {@link ParticipantIdentity#getTenantId()}
     * of the tenant's users.
     */
    private String id;

    private String organizationId;

    /** The application the tenant belongs to, one of the organization's. */
    private String applicationId;

    private String name;

    private String description;

    private Date created;

    private Date updated;
}
