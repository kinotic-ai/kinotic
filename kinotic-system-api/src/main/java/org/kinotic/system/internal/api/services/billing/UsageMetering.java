package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.utils.CursorPaging;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.kinotic.management.api.model.billing.UsageLine;
import org.kinotic.management.api.model.billing.UsageMeasurementType;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.management.api.model.billing.UsageRecord;
import org.kinotic.system.internal.api.model.BillableResource;
import org.kinotic.system.internal.api.model.MeteredAmount;
import org.kinotic.system.internal.api.model.UsageReading;
import org.kinotic.system.internal.api.repositories.BillableResourceRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records each organization's usage of Kinotic Cloud into hourly {@link UsageRecord}s and reports it to Stripe. While
 * an hour runs, {@link #poll(Instant)} adds what each {@link UsageSource} reads; once it is over,
 * {@link #settle(Date)} adds what each {@link BillableResource} used during it, reports the hour's usage to
 * Stripe, and marks the record settled. Polling or settling the same hour again, from any server, changes nothing
 * once the hour is settled, and each meter's usage of an hour is reported to Stripe until Stripe accepts it.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class UsageMetering {

    /** How long after an hour ends it is settled, so the runs that stopped during it are known by then. */
    public static final Duration SETTLE_DELAY = Duration.ofMinutes(10);

    private static final Duration HOUR = Duration.ofHours(1);
    private static final double MILLIS_PER_HOUR = HOUR.toMillis();
    private static final int PAGE_SIZE = 500;

    private final List<UsageSource> sources;
    private final BillableResourceRepository resourceRepository;
    private final UsageRecordRepository usageRepository;
    private final BillingAccountRepository accountRepository;
    private final StripeBilling stripeBilling;

    /**
     * Reads every {@link UsageSource} and records what it reads as the usage of the hour {@code now} falls in, in place
     * of what an earlier read of the same hour recorded.
     */
    public Future<Void> poll(Instant now) {
        Date hour = Date.from(now.truncatedTo(ChronoUnit.HOURS));
        List<Future<Void>> reading = sources.stream().map(source -> source.read().compose(readings -> record(hour, readings))).toList();
        return Future.all(reading).mapEmpty();
    }

    private Future<Void> record(Date hour, List<UsageReading> readings) {
        Map<String, List<UsageLine>> byOrganization = readings.stream()
                .collect(Collectors.groupingBy(UsageReading::organizationId,
                                               Collectors.mapping(UsageReading::line, Collectors.toList())));
        List<Future<UsageRecord>> recording = new ArrayList<>();
        byOrganization.forEach((organizationId, lines) -> {
            Set<UsageMeter> meters = lines.stream().map(UsageLine::getMeter).collect(Collectors.toSet());
            recording.add(usageRepository.update(UsageRecordRepository.idOf(organizationId, hour), stored -> {
                UsageRecord record = stored != null ? stored : newRecord(organizationId, hour);
                if (record.getSettled() == null) {
                    record.getLines().removeIf(line -> meters.contains(line.getMeter()));
                    record.getLines().addAll(lines);
                    record.setUpdated(new Date());
                }
                return Future.succeededFuture(record);
            }));
        });
        return Future.all(recording).mapEmpty();
    }

    /**
     * Settles the hour starting at {@code hour} for every organization that used anything during it: records what each
     * of its runs used, reports the hour's usage to Stripe when the organization was subscribed to a plan during it
     * after its trial, and marks the record settled.
     *
     * @param hour the start of an hour that ended at least {@link #SETTLE_DELAY} ago
     */
    public Future<Void> settle(Date hour) {
        Date end = Date.from(hour.toInstant().plus(HOUR));
        Map<String, List<UsageLine>> usage = new HashMap<>();
        return CursorPaging.forEachPage(pageable -> resourceRepository.findRunningDuring(hour, end, pageable), PAGE_SIZE, Sort.by("id"),
                                        resources -> {
                                            resources.forEach(resource -> usage.computeIfAbsent(resource.getOrganizationId(), id -> new ArrayList<>())
                                                                               .addAll(linesOf(resource, hour, end)));
                                            return Future.succeededFuture();
                                        })
                .compose(v -> CursorPaging.forEachPage(pageable -> usageRepository.findUnsettledOfHour(hour, pageable), PAGE_SIZE, Sort.by("id"),
                                                       records -> {
                                                           records.forEach(record -> usage.computeIfAbsent(record.getOrganizationId(), id -> new ArrayList<>()));
                                                           return Future.succeededFuture();
                                                       }))
                // every organization is settled however many fail, and the hour fails if any of them does
                .compose(v -> Future.join(usage.entrySet().stream()
                                              .map(entry -> settle(entry.getKey(), hour, merged(entry.getValue())))
                                              .toList())
                                    .mapEmpty());
    }

    // what a run used of each meter during the part of the hour it ran
    private static List<UsageLine> linesOf(BillableResource resource, Date hour, Date end) {
        long from = Math.max(resource.getStarted().getTime(), hour.getTime());
        long to = Math.min(resource.getEnded() != null ? resource.getEnded().getTime() : end.getTime(), end.getTime());
        double hours = Math.max(to - from, 0) / MILLIS_PER_HOUR;
        List<UsageLine> ret = new ArrayList<>();
        for (MeteredAmount amount : resource.getAmounts()) {
            ret.add(new UsageLine().setMeter(amount.getMeter())
                                   .setSubjectId(resource.getSubjectId())
                                   .setSubjectName(resource.getSubjectName())
                                   .setQuantity(amount.getPerHour() * hours));
        }
        return ret;
    }

    // one line per meter and subject, adding up the runs a subject had during the hour
    private static List<UsageLine> merged(List<UsageLine> lines) {
        Map<String, UsageLine> ret = new HashMap<>();
        for (UsageLine line : lines) {
            ret.merge(line.getMeter() + ":" + line.getSubjectId(),
                      new UsageLine().setMeter(line.getMeter())
                                     .setSubjectId(line.getSubjectId())
                                     .setSubjectName(line.getSubjectName())
                                     .setQuantity(line.getQuantity()),
                      (a, b) -> a.setQuantity(a.getQuantity() + b.getQuantity()));
        }
        return new ArrayList<>(ret.values());
    }

    private Future<Void> settle(String organizationId, Date hour, List<UsageLine> resourceLines) {
        String id = UsageRecordRepository.idOf(organizationId, hour);
        return usageRepository.findById(id).compose(found -> found != null && found.getSettled() != null
                ? Future.succeededFuture()
                : usageRepository.update(id, stored -> {
                                     UsageRecord record = stored != null ? stored : newRecord(organizationId, hour);
                                     if (record.getSettled() == null) {
                                         record.getLines().removeIf(line -> line.getMeter().getMeasurementType() == UsageMeasurementType.RUNNING_RESOURCE);
                                         record.getLines().addAll(resourceLines);
                                         record.setUpdated(new Date());
                                     }
                                     return Future.succeededFuture(record);
                                 })
                                 .compose(record -> record.getSettled() != null ? Future.succeededFuture() : bill(record))
                                 .compose(v -> usageRepository.update(id, stored -> {
                                     if (stored.getSettled() == null) {
                                         stored.setSettled(new Date()).setUpdated(new Date());
                                     }
                                     return Future.succeededFuture(stored);
                                 }))
                                 .mapEmpty());
    }

    private Future<Void> bill(UsageRecord record) {
        return accountRepository.findById(record.getOrganizationId())
                                .compose(account -> billable(account, record) ? report(account, record) : Future.succeededFuture());
    }

    // an hour is billed when it is after the trial and overlaps the time of a subscription, which may since have ended
    private static boolean billable(BillingAccount account, UsageRecord record) {
        Date hour = record.getHour();
        Date end = Date.from(hour.toInstant().plus(HOUR));
        return account != null
                && account.getStripeCustomerId() != null
                && !hour.before(account.getTrialEndsAt())
                && account.getSubscriptionPeriods().stream()
                          .anyMatch(period -> period.getStarted().before(end) && (period.getEnded() == null || hour.before(period.getEnded())));
    }

    // Each meter's report is recorded as soon as Stripe accepts it, so a retry reports only the meters not yet
    // accepted. The identifier names the hour and meter, so Stripe also drops a report repeated within its 24-hour
    // deduplication window, which covers a retry after the record of an accepted report failed to save.
    private Future<Void> report(BillingAccount account, UsageRecord record) {
        Instant midHour = record.getHour().toInstant().plus(Duration.ofMinutes(30));
        Map<UsageMeter, Double> totals = new EnumMap<>(UsageMeter.class);
        record.getLines().forEach(line -> totals.merge(line.getMeter(), line.getQuantity(), Double::sum));
        List<Future<Void>> reporting = totals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && !record.getReported().contains(entry.getKey()))
                .map(entry -> stripeBilling.reportUsage(account.getStripeCustomerId(),
                                                        entry.getKey(),
                                                        record.getId() + ":" + entry.getKey().name(),
                                                        entry.getValue(),
                                                        midHour)
                                           .compose(v -> usageRepository.update(record.getId(), stored -> {
                                               if (!stored.getReported().contains(entry.getKey())) {
                                                   stored.getReported().add(entry.getKey());
                                                   stored.setUpdated(new Date());
                                               }
                                               return Future.succeededFuture(stored);
                                           }))
                                           .<Void>mapEmpty())
                .toList();
        return Future.all(reporting).mapEmpty();
    }

    private static UsageRecord newRecord(String organizationId, Date hour) {
        Date now = new Date();
        return new UsageRecord().setId(UsageRecordRepository.idOf(organizationId, hour))
                                .setOrganizationId(organizationId)
                                .setHour(hour)
                                .setCreated(now)
                                .setUpdated(now);
    }
}
