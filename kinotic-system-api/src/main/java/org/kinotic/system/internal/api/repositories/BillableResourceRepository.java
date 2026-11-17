package org.kinotic.system.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.system.internal.api.model.BillableResource;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class BillableResourceRepository extends AbstractRepository<BillableResource> {

    // more runs than any one subject or organization has open at once
    private static final int MAX_OPEN = 1_000;

    public BillableResourceRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_billable_resource", BillableResource.class, crudServiceTemplate);
    }

    /** The runs of the subject that have not ended. */
    public Future<List<BillableResource>> findOpenForSubject(String subjectId) {
        return findAll(Pageable.ofSize(MAX_OPEN), b -> b.query(composeFilter(termFilter("subjectId", subjectId), missingFilter("ended"))))
                .map(Page::getContent);
    }

    /** The organization's runs that have not ended. */
    public Future<List<BillableResource>> findOpenForOrganization(String organizationId) {
        return findAll(Pageable.ofSize(MAX_OPEN), b -> b.query(composeFilter(termFilter("organizationId", organizationId), missingFilter("ended"))))
                .map(Page::getContent);
    }

    /** The runs of every organization that ran at some time from {@code from} up to {@code to}, in the order of their ids. */
    public Future<Page<BillableResource>> findRunningDuring(Date from, Date to, CursorPageable pageable) {
        Query startedBefore = Query.of(q -> q.range(r -> r.date(d -> d.field("started").lt(to.toInstant().toString()))));
        Query endedAfter = Query.of(q -> q.range(r -> r.date(d -> d.field("ended").gt(from.toInstant().toString()))));
        Query notEndedBefore = Query.of(q -> q.bool(b -> b.should(missingFilter("ended"), endedAfter).minimumShouldMatch("1")));
        return findAll(pageable, b -> b.query(composeFilter(startedBefore, notEndedBefore)));
    }
}
