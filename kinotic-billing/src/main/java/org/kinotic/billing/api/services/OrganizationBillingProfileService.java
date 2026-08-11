package org.kinotic.billing.api.services;

import org.kinotic.billing.api.model.OrganizationBillingProfile;
import org.kinotic.core.api.crud.IdentifiableCrudService;

/**
 * Manages each organization's billing profile. The profile id always equals the
 * organization id — there is exactly one profile per organization.
 */
public interface OrganizationBillingProfileService extends IdentifiableCrudService<OrganizationBillingProfile, String> {
}
