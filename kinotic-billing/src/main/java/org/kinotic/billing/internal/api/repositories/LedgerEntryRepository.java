package org.kinotic.billing.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.SumAggregate;
import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.model.LedgerEntry;
import org.kinotic.billing.api.model.LedgerEntryType;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class LedgerEntryRepository extends AbstractOrganizationScopedRepository<LedgerEntry> {

    public LedgerEntryRepository(ElasticsearchAsyncClient esAsyncClient,
                                 CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_ledger_entry", LedgerEntry.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Sums entry amounts per {@link LedgerEntryType} for the given organization. Types with
     * no entries are absent from the returned map.
     * <p>
     * The journal is append-only and unbounded, so this is a single {@code size(0)}
     * aggregation rather than a paginated fold. ES sums longs as doubles — exact up to
     * 2^53 minor units, far beyond any realistic balance.
     */
    public CompletableFuture<Map<LedgerEntryType, Long>> sumAmountsByEntryType(String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return esAsyncClient.search(s -> s
                                    .index(indexName)
                                    .size(0)
                                    .routing(orgId)
                                    .query(composeOrgFilter(orgId))
                                    .aggregations("by_entry_type", a -> a
                                            .terms(t -> t.field("entryType")
                                                         .size(LedgerEntryType.values().length))
                                            .aggregations("total", sub -> sub.sum(su -> su.field("amount")))),
                            Void.class)
                            .thenApply(response -> {
                                Map<LedgerEntryType, Long> sums = new EnumMap<>(LedgerEntryType.class);
                                for (StringTermsBucket bucket : response.aggregations()
                                                                        .get("by_entry_type")
                                                                        .sterms()
                                                                        .buckets()
                                                                        .array()) {
                                    SumAggregate total = bucket.aggregations().get("total").sum();
                                    sums.put(LedgerEntryType.valueOf(bucket.key().stringValue()),
                                             total.value().longValue());
                                }
                                return sums;
                            });
    }
}
