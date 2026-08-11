package org.kinotic.billing.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.config.BillingConstants;
import org.kinotic.billing.api.model.LedgerEntry;
import org.kinotic.billing.api.model.LedgerEntryType;
import org.kinotic.billing.api.model.MerchantOfRecord;
import org.kinotic.billing.api.model.OrganizationBillingProfile;
import org.kinotic.billing.api.model.RevenueSplit;
import org.kinotic.billing.api.model.RevenueSplitStatus;
import org.kinotic.billing.api.services.RevenueSplitService;
import org.kinotic.billing.internal.api.repositories.LedgerEntryRepository;
import org.kinotic.billing.internal.api.repositories.OrganizationBillingProfileRepository;
import org.kinotic.billing.internal.api.repositories.RevenueSplitRepository;
import org.kinotic.billing.internal.api.services.client.StripeChargeDetails;
import org.kinotic.billing.internal.api.services.client.StripeClientFacade;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRevenueSplitService implements RevenueSplitService {

    private final RevenueSplitRepository revenueSplitRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OrganizationBillingProfileRepository profileRepository;
    private final StripeClientFacade stripeClientFacade;
    private final PlatformFeePolicy platformFeePolicy;

    @Override
    public CompletableFuture<RevenueSplit> recordCharge(String stripeChargeId) {
        Validate.notBlank(stripeChargeId, "stripeChargeId cannot be blank");
        // Org resolution comes from the charge's own metadata (webhooks carry no participant),
        // so the explicit-orgId repository overloads are used throughout.
        return stripeClientFacade.fetchChargeDetails(stripeChargeId)
                                 .thenCompose(details -> {
                                     if (details == null) {
                                         return CompletableFuture.failedFuture(new IllegalStateException(
                                                 "No Stripe charge found for id " + stripeChargeId));
                                     }
                                     String orgId = details.getOrgId();
                                     if (orgId == null || orgId.isBlank()) {
                                         return CompletableFuture.failedFuture(new IllegalStateException(
                                                 "Charge " + stripeChargeId + " carries no orgId metadata — not a "
                                                 + "Kinotic-created charge or the metadata contract was violated"));
                                     }
                                     if (details.getStripeFeeAmount() == null) {
                                         // Balance transaction not settled yet (async payment methods land
                                         // here) — fail so the webhook retry machinery re-drives us later.
                                         return CompletableFuture.failedFuture(new IllegalStateException(
                                                 "Charge " + stripeChargeId + " has no settled balance transaction yet"));
                                     }
                                     if (!BillingConstants.LEDGER_CURRENCY.equals(details.getCurrency())) {
                                         return CompletableFuture.failedFuture(new IllegalStateException(
                                                 "Charge " + stripeChargeId + " is in currency '" + details.getCurrency()
                                                 + "' but the ledger operates in '" + BillingConstants.LEDGER_CURRENCY + "' only"));
                                     }
                                     return recordForOrg(details, orgId);
                                 });
    }

    private CompletableFuture<RevenueSplit> recordForOrg(StripeChargeDetails details, String orgId) {
        return profileRepository.findById(orgId, orgId)
                                .thenCompose(profile -> {
                                    if (profile == null) {
                                        return CompletableFuture.failedFuture(new IllegalStateException(
                                                "No OrganizationBillingProfile for org " + orgId));
                                    }
                                    if (profile.getMerchantOfRecord() != MerchantOfRecord.KINOTIC) {
                                        return CompletableFuture.failedFuture(new IllegalStateException(
                                                "Org " + orgId + " uses merchantOfRecord "
                                                + profile.getMerchantOfRecord()
                                                + " — the Kinotic-MoR split rail does not apply"));
                                    }
                                    return revenueSplitRepository.findById(details.getChargeId(), orgId)
                                                                 .thenCompose(existing -> existing != null
                                                                         ? ensureEarningEntryPosted(existing)
                                                                         : createSplit(details, orgId, profile));
                                });
    }

    private CompletableFuture<RevenueSplit> createSplit(StripeChargeDetails details,
                                                        String orgId,
                                                        OrganizationBillingProfile profile) {
        return platformFeePolicy.feeBasisPointsFor(profile)
                                .thenCompose(feeBasisPoints -> {
                                    RevenueSplitAmounts amounts = RevenueSplitCalculator.compute(
                                            details.getAmountTotal(),
                                            details.getTaxAmount(),
                                            details.getStripeFeeAmount(),
                                            feeBasisPoints);
                                    Date now = new Date();
                                    RevenueSplit split = new RevenueSplit()
                                            .setId(details.getChargeId())
                                            .setOrganizationId(orgId)
                                            .setApplicationId(details.getApplicationId())
                                            .setTenantId(details.getTenantId())
                                            .setStripePaymentIntentId(details.getPaymentIntentId())
                                            .setEndUserStripeCustomerId(details.getEndUserCustomerId())
                                            .setAmountTotal(details.getAmountTotal())
                                            .setTaxAmount(details.getTaxAmount())
                                            .setStripeFeeAmount(details.getStripeFeeAmount())
                                            .setPlatformFeeAmount(amounts.platformFeeAmount())
                                            .setPlatformFeeBasisPoints(feeBasisPoints)
                                            .setPricingPlanId(profile.getPricingPlanId())
                                            .setNetToOrganization(amounts.netToOrganization())
                                            .setStatus(RevenueSplitStatus.EARNED)
                                            .setCreated(now)
                                            .setUpdated(now);
                                    // Save order matters for crash recovery: a split without its earning
                                    // entry is healed by ensureEarningEntryPosted on replay; the reverse
                                    // would credit money with no explanation attached.
                                    return revenueSplitRepository.save(split, orgId)
                                                                 .thenCompose(saved -> ledgerEntryRepository.save(earningEntryFor(saved), orgId)
                                                                                                            .thenApply(entry -> saved));
                                });
    }

    private CompletableFuture<RevenueSplit> ensureEarningEntryPosted(RevenueSplit split) {
        String orgId = split.getOrganizationId();
        String entryId = LedgerEntryIds.entryId(split.getId(), LedgerEntryType.EARNING);
        return ledgerEntryRepository.findById(entryId, orgId)
                                    .thenCompose(entry -> {
                                        if (entry != null) {
                                            return CompletableFuture.completedFuture(split);
                                        }
                                        // Crash window: the split was persisted but its earning entry
                                        // wasn't. Repost from the stored split so the replay converges.
                                        return ledgerEntryRepository.save(earningEntryFor(split), orgId)
                                                                    .thenApply(saved -> split);
                                    });
    }

    private LedgerEntry earningEntryFor(RevenueSplit split) {
        return new LedgerEntry()
                .setId(LedgerEntryIds.entryId(split.getId(), LedgerEntryType.EARNING))
                .setOrganizationId(split.getOrganizationId())
                .setApplicationId(split.getApplicationId())
                .setEntryType(LedgerEntryType.EARNING)
                .setAmount(split.getNetToOrganization())
                .setCurrency(BillingConstants.LEDGER_CURRENCY)
                .setStripeObjectId(split.getId())
                .setRevenueSplitId(split.getId())
                .setCreated(split.getCreated());
    }
}
