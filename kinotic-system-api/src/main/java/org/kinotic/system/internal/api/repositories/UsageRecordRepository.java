package org.kinotic.system.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.billing.UsageRecord;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class UsageRecordRepository extends AbstractRepository<UsageRecord> {

    // an hourly record for every hour of the longest range findForOrganization is asked for, with room to spare
    private static final int MAX_RECORDS = 5_000;

    public UsageRecordRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_usage_record", UsageRecord.class, crudServiceTemplate);
    }

    /** The id of the organization's record of the hour starting at {@code hour}. */
    public static String idOf(String organizationId, Date hour) {
        return organizationId + ":" + hour.toInstant().getEpochSecond();
    }

    /**
     * The organization's records of the hours starting from {@code from} up to {@code to}, oldest first.
     */
    public Future<List<UsageRecord>> findForOrganization(String organizationId, Date from, Date to) {
        return findAll(Pageable.create(0, MAX_RECORDS, Sort.by("hour")),
                       b -> b.query(composeFilter(termFilter("organizationId", organizationId), hourRange(from, to))))
                .map(Page::getContent);
    }

    /**
     * The records not yet settled of the hours that started before {@code before}, in the order of their ids.
     */
    public Future<Page<UsageRecord>> findUnsettledBefore(Date before, CursorPageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(missingFilter("settled"), hourRange(null, before))));
    }

    private static Query hourRange(Date from, Date to) {
        return Query.of(q -> q.range(r -> r.date(d -> {
            d.field("hour");
            if (from != null) {
                d.gte(from.toInstant().toString());
            }
            return d.lt(to.toInstant().toString());
        })));
    }
}
