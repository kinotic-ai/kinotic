package org.kinotic.billing.internal.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.billing.api.model.LedgerEntry;
import org.kinotic.billing.api.model.LedgerEntryType;
import org.kinotic.billing.api.model.MerchantOfRecord;
import org.kinotic.billing.api.model.OrgBillingStatus;
import org.kinotic.billing.api.model.OrganizationBillingProfile;
import org.kinotic.billing.api.model.RevenueSplit;
import org.kinotic.billing.api.model.RevenueSplitStatus;
import org.kinotic.billing.internal.api.repositories.LedgerEntryRepository;
import org.kinotic.billing.internal.api.repositories.OrganizationBillingProfileRepository;
import org.kinotic.billing.internal.api.repositories.RevenueSplitRepository;
import org.kinotic.billing.internal.api.services.client.StripeChargeDetails;
import org.kinotic.billing.internal.api.services.client.StripeClientFacade;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultRevenueSplitServiceTest {

    private static final String ORG_ID = "org1";
    private static final String CHARGE_ID = "ch_test123";

    private InMemoryRevenueSplitRepository splitRepository;
    private InMemoryLedgerEntryRepository ledgerRepository;
    private ScriptedProfileRepository profileRepository;
    private ScriptedStripeClientFacade facade;
    private DefaultRevenueSplitService service;

    @BeforeEach
    public void setUp() {
        splitRepository = new InMemoryRevenueSplitRepository();
        ledgerRepository = new InMemoryLedgerEntryRepository();
        profileRepository = new ScriptedProfileRepository();
        facade = new ScriptedStripeClientFacade();
        service = new DefaultRevenueSplitService(splitRepository,
                                                 ledgerRepository,
                                                 profileRepository,
                                                 facade,
                                                 profile -> CompletableFuture.completedFuture(500L));

        profileRepository.profile = new OrganizationBillingProfile()
                .setId(ORG_ID)
                .setOrganizationId(ORG_ID)
                .setMerchantOfRecord(MerchantOfRecord.KINOTIC)
                .setBillingStatus(OrgBillingStatus.ACTIVE);
        facade.details = kinoticCharge();
    }

    private StripeChargeDetails kinoticCharge() {
        return new StripeChargeDetails()
                .setChargeId(CHARGE_ID)
                .setPaymentIntentId("pi_test123")
                .setAmountTotal(5000)
                .setCurrency("usd")
                .setLivemode(false)
                .setOrgId(ORG_ID)
                .setApplicationId("app1")
                .setTenantId("tenant1")
                .setStripeFeeAmount(175L)
                .setTaxAmount(0);
    }

    @Test
    public void whenChargeUnseen_thenSplitPersistedAndEarningEntryPosted() {
        RevenueSplit split = service.recordCharge(CHARGE_ID).join();

        assertEquals(CHARGE_ID, split.getId());
        assertEquals(RevenueSplitStatus.EARNED, split.getStatus());
        assertEquals(250, split.getPlatformFeeAmount());
        assertEquals(4575, split.getNetToOrganization());
        assertEquals(split.getAmountTotal() - split.getTaxAmount() - split.getStripeFeeAmount()
                             - split.getPlatformFeeAmount(),
                     split.getNetToOrganization());

        LedgerEntry earning = ledgerRepository.store.get("ch_test123:EARNING");
        assertNotNull(earning);
        assertEquals(LedgerEntryType.EARNING, earning.getEntryType());
        assertEquals(4575, earning.getAmount());
        assertEquals(CHARGE_ID, earning.getRevenueSplitId());
        assertEquals(ORG_ID, earning.getOrganizationId());
    }

    @Test
    public void whenChargeAlreadyRecorded_thenNoOpAndNoDuplicateLedgerEntry() {
        RevenueSplit existing = existingSplit();
        splitRepository.store.put(CHARGE_ID, existing);
        ledgerRepository.store.put("ch_test123:EARNING",
                                   new LedgerEntry().setId("ch_test123:EARNING").setOrganizationId(ORG_ID));

        RevenueSplit result = service.recordCharge(CHARGE_ID).join();

        assertSame(existing, result);
        assertEquals(0, splitRepository.saveCount);
        assertEquals(0, ledgerRepository.saveCount);
    }

    @Test
    public void whenSplitExistsButEarningEntryMissing_thenEntryRepostedFromSplit() {
        // Crash window: the split was saved but the process died before the earning entry
        // landed — replaying the charge must heal the ledger.
        RevenueSplit existing = existingSplit();
        splitRepository.store.put(CHARGE_ID, existing);

        RevenueSplit result = service.recordCharge(CHARGE_ID).join();

        assertSame(existing, result);
        assertEquals(0, splitRepository.saveCount);
        assertEquals(1, ledgerRepository.saveCount);
        LedgerEntry reposted = ledgerRepository.store.get("ch_test123:EARNING");
        assertNotNull(reposted);
        assertEquals(existing.getNetToOrganization(), reposted.getAmount());
        assertEquals(ORG_ID, reposted.getOrganizationId());
    }

    @Test
    public void whenChargeCurrencyIsNotLedgerCurrency_thenFailsWithoutPersisting() {
        facade.details.setCurrency("eur");

        CompletionException error = assertThrows(CompletionException.class,
                                                 () -> service.recordCharge(CHARGE_ID).join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(0, splitRepository.saveCount);
        assertEquals(0, ledgerRepository.saveCount);
    }

    private RevenueSplit existingSplit() {
        return new RevenueSplit().setId(CHARGE_ID)
                                 .setOrganizationId(ORG_ID)
                                 .setApplicationId("app1")
                                 .setNetToOrganization(4575)
                                 .setStatus(RevenueSplitStatus.EARNED)
                                 .setCreated(new Date());
    }

    @Test
    public void whenChargeMissingOrgMetadata_thenFailsAndNothingPersisted() {
        facade.details.setOrgId(null);

        CompletionException error = assertThrows(CompletionException.class,
                                                 () -> service.recordCharge(CHARGE_ID).join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(0, splitRepository.saveCount);
        assertEquals(0, ledgerRepository.saveCount);
    }

    @Test
    public void whenBalanceTransactionUnresolved_thenFailsSoRetryCanRedrive() {
        facade.details.setStripeFeeAmount(null);

        CompletionException error = assertThrows(CompletionException.class,
                                                 () -> service.recordCharge(CHARGE_ID).join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("balance transaction"));
        assertEquals(0, splitRepository.saveCount);
        assertEquals(0, ledgerRepository.saveCount);
    }

    @Test
    public void whenProfileMerchantOfRecordIsOrganization_thenFailsWithoutPersisting() {
        profileRepository.profile.setMerchantOfRecord(MerchantOfRecord.ORGANIZATION);

        CompletionException error = assertThrows(CompletionException.class,
                                                 () -> service.recordCharge(CHARGE_ID).join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(0, splitRepository.saveCount);
        assertEquals(0, ledgerRepository.saveCount);
    }

    @Test
    public void whenProfileMissing_thenFailsWithoutPersisting() {
        profileRepository.profile = null;

        CompletionException error = assertThrows(CompletionException.class,
                                                 () -> service.recordCharge(CHARGE_ID).join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(0, splitRepository.saveCount);
    }

    // ---- fakes: super deps are null; only the overridden methods are ever invoked ----

    static class InMemoryRevenueSplitRepository extends RevenueSplitRepository {
        final Map<String, RevenueSplit> store = new HashMap<>();
        int saveCount = 0;

        InMemoryRevenueSplitRepository() {
            super(null, null);
        }

        @Override
        public CompletableFuture<RevenueSplit> findById(String id, String orgId) {
            return CompletableFuture.completedFuture(store.get(id));
        }

        @Override
        public CompletableFuture<RevenueSplit> save(RevenueSplit value, String orgId) {
            saveCount++;
            store.put(value.getId(), value);
            return CompletableFuture.completedFuture(value);
        }
    }

    static class InMemoryLedgerEntryRepository extends LedgerEntryRepository {
        final Map<String, LedgerEntry> store = new HashMap<>();
        int saveCount = 0;

        InMemoryLedgerEntryRepository() {
            super(null, null);
        }

        @Override
        public CompletableFuture<LedgerEntry> findById(String id, String orgId) {
            return CompletableFuture.completedFuture(store.get(id));
        }

        @Override
        public CompletableFuture<LedgerEntry> save(LedgerEntry value, String orgId) {
            saveCount++;
            store.put(value.getId(), value);
            return CompletableFuture.completedFuture(value);
        }
    }

    static class ScriptedProfileRepository extends OrganizationBillingProfileRepository {
        OrganizationBillingProfile profile;

        ScriptedProfileRepository() {
            super(null, null);
        }

        @Override
        public CompletableFuture<OrganizationBillingProfile> findById(String id, String orgId) {
            return CompletableFuture.completedFuture(profile);
        }
    }

    static class ScriptedStripeClientFacade implements StripeClientFacade {
        StripeChargeDetails details;

        @Override
        public CompletableFuture<StripeChargeDetails> fetchChargeDetails(String stripeChargeId) {
            return CompletableFuture.completedFuture(details);
        }
    }
}
