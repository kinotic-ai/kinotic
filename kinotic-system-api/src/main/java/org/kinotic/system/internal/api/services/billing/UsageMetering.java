package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.utils.CursorPaging;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.management.api.model.billing.UsageRecord;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.internal.api.repositories.BillingAccountRepository;
import org.kinotic.system.internal.api.repositories.UsageRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures each organization's use of Kinotic Cloud into hourly {@link UsageRecord}s and reports it to Stripe. Any
 * number of system servers may measure or settle the same slot or hour at once: each slot of an hour counts once,
 * and each hour is reported to Stripe once.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class UsageMetering {

    /** How long one measurement stands for. */
    public static final Duration SLOT = Duration.ofMinutes(5);

    private static final double MB_PER_GB = 1024;
    private static final double SLOT_HOURS = SLOT.toMinutes() / 60.0;
    // the last slot of an hour is measured within a slot of the hour's end, so the hour is complete by then
    private static final Duration SETTLE_AFTER = Duration.ofHours(1).plus(SLOT.multipliedBy(2));
    // Stripe accepts usage from the past 35 days
    private static final Duration REPORTABLE = Duration.ofDays(34);
    private static final int PAGE_SIZE = 500;

    private final WorkloadRepository workloadRepository;
    private final UsageRecordRepository usageRepository;
    private final BillingAccountRepository accountRepository;
    private final StripeBilling stripeBilling;

    /**
     * Measures the slot {@code now} falls in: adds the vCPUs and memory of each organization's running workload VMs,
     * for the length of a slot, to the organization's record of the hour, unless the slot is measured already.
     */
    public Future<Void> measure(Instant now) {
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        int slot = (int) (Duration.between(hour, now).toMillis() / SLOT.toMillis());
        Map<String, Map<UsageMeter, Double>> usage = new HashMap<>();
        return CursorPaging.forEachPage(workloadRepository::findRunningForOrganizations, PAGE_SIZE, Sort.by("id"), workloads -> {
            for (Workload workload : workloads) {
                Map<UsageMeter, Double> quantities = usage.computeIfAbsent(workload.getOrganizationId(),
                                                                           id -> new EnumMap<>(UsageMeter.class));
                quantities.merge(UsageMeter.VCPU_HOURS, workload.getCpus() * SLOT_HOURS, Double::sum);
                quantities.merge(UsageMeter.MEMORY_GB_HOURS, workload.getMemoryMb() / MB_PER_GB * SLOT_HOURS, Double::sum);
            }
            return Future.succeededFuture();
        }).compose(v -> {
            List<Future<UsageRecord>> recording = new ArrayList<>();
            usage.forEach((organizationId, quantities) -> recording.add(record(organizationId, Date.from(hour), slot, quantities)));
            return Future.all(recording).mapEmpty();
        });
    }

    private Future<UsageRecord> record(String organizationId, Date hour, int slot, Map<UsageMeter, Double> quantities) {
        return usageRepository.update(UsageRecordRepository.idOf(organizationId, hour), stored -> {
            Date now = new Date();
            UsageRecord record = stored != null
                    ? stored
                    : new UsageRecord().setId(UsageRecordRepository.idOf(organizationId, hour))
                                       .setOrganizationId(organizationId)
                                       .setHour(hour)
                                       .setCreated(now);
            if (!record.getMeasuredSlots().contains(slot)) {
                quantities.forEach((meter, quantity) -> record.getQuantities().merge(meter, quantity, Double::sum));
                record.getMeasuredSlots().add(slot);
                record.setUpdated(now);
            }
            return Future.succeededFuture(record);
        });
    }

    /**
     * Settles every hour that ended long enough before {@code now} to be completely measured: reports its usage to
     * Stripe when the organization subscribed to a plan for all of it after its trial, and marks it settled.
     */
    public Future<Void> settle(Instant now) {
        Date before = Date.from(now.minus(SETTLE_AFTER));
        return CursorPaging.forEachPage(pageable -> usageRepository.findUnsettledBefore(before, pageable),
                                        PAGE_SIZE,
                                        Sort.by("id"),
                                        records -> Future.all(records.stream().map(record -> settle(record, now)).toList())
                                                         .mapEmpty());
    }

    private Future<UsageRecord> settle(UsageRecord record, Instant now) {
        return accountRepository.findById(record.getOrganizationId())
                                .compose(account -> billable(account, record, now) ? report(account, record) : Future.succeededFuture())
                                .compose(v -> usageRepository.update(record.getId(), stored -> {
                                    if (stored.getSettled() == null) {
                                        stored.setSettled(new Date()).setUpdated(new Date());
                                    }
                                    return Future.succeededFuture(stored);
                                }));
    }

    private static boolean billable(BillingAccount account, UsageRecord record, Instant now) {
        boolean ret = false;
        if (account != null && account.getStripeSubscriptionId() != null && account.getStripeCustomerId() != null) {
            Instant hour = record.getHour().toInstant();
            if (hour.isBefore(now.minus(REPORTABLE))) {
                log.warn("The usage of organization {} in the hour from {} is too old for Stripe to take, so it is left unbilled",
                         record.getOrganizationId(), hour);
            } else {
                ret = !hour.isBefore(account.getTrialEndsAt().toInstant());
            }
        }
        return ret;
    }

    // the identifier names the hour and meter, so Stripe counts the usage once however many times it is reported
    private Future<Void> report(BillingAccount account, UsageRecord record) {
        Instant midHour = record.getHour().toInstant().plus(Duration.ofMinutes(30));
        List<Future<Void>> reporting = record.getQuantities().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> stripeBilling.reportUsage(account.getStripeCustomerId(),
                                                        entry.getKey(),
                                                        record.getId() + ":" + entry.getKey().name(),
                                                        entry.getValue(),
                                                        midHour))
                .toList();
        return Future.all(reporting).mapEmpty();
    }
}
