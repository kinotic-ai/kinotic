package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Provisions a brand-new Organization together with its first admin — the step both sign-up
 * flows share once the email is settled.
 */
@Component
@RequiredArgsConstructor
public class OrgAdminProvisioner {

    private final OrganizationService organizationService;
    private final IamUserRepository iamUserRepository;

    /**
     * Creates the Organization from the given name/description and makes {@code admin} its first
     * member: scopes the admin to the new org, persists it, then records it as the org's creator.
     * Returns the saved admin, now carrying its {@code organizationId}.
     *
     * @param orgName        name for the new organization
     * @param orgDescription description for the new organization (may be null)
     * @param admin          the admin user to create, fully populated except for organizationId
     */
    public CompletableFuture<IamUser> provisionNewOrg(String orgName, String orgDescription, IamUser admin) {
        Organization org = new Organization()
                .setName(orgName)
                .setDescription(orgDescription);
        return organizationService.save(org)
                .thenCompose(savedOrg -> {
                    admin.setOrganizationId(savedOrg.getId());
                    return iamUserRepository.save(admin)
                                            .thenCompose(savedAdmin -> {
                                                savedOrg.setCreatedBy(savedAdmin.getId());
                                                return organizationService.save(savedOrg)
                                                                          .thenApply(o -> savedAdmin);
                                            });
                });
    }
}
