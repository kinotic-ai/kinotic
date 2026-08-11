package org.kinotic.billing.internal.api.services;

import org.kinotic.billing.api.config.BillingConstants;
import org.kinotic.billing.api.model.OrganizationBillingProfile;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DefaultPlatformFeePolicy implements PlatformFeePolicy {

    @Override
    public CompletableFuture<Long> feeBasisPointsFor(OrganizationBillingProfile profile) {
        return CompletableFuture.completedFuture(BillingConstants.DEFAULT_PLATFORM_FEE_BASIS_POINTS);
    }
}
