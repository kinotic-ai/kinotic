package org.kinotic.billing.internal.api.services;

import org.kinotic.billing.api.model.OrganizationBillingProfile;

import java.util.concurrent.CompletableFuture;

/**
 * Resolves the platform take rate for an organization. The plan-catalog round replaces the
 * default constant-rate implementation with one that resolves the profile's pricing plan.
 */
public interface PlatformFeePolicy {

    /**
     * @return the take rate to apply to the organization's charges, in basis points
     */
    CompletableFuture<Long> feeBasisPointsFor(OrganizationBillingProfile profile);
}
