package org.kinotic.system.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.billing.BillingPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingPlanRepository extends AbstractRepository<BillingPlan> {

    // no organization is offered anywhere near this many plans
    private static final int MAX_PLANS = 500;

    public BillingPlanRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_billing_plan", BillingPlan.class, crudServiceTemplate);
    }

    /**
     * The plans the organization may subscribe to: those not archived that are offered to every organization or to it
     * alone, ordered by name.
     */
    public Future<List<BillingPlan>> findOffered(String organizationId) {
        Query offeredTo = Query.of(q -> q.bool(b -> b.should(missingFilter("organizationId"))
                                                    .should(termFilter("organizationId", organizationId))
                                                    .minimumShouldMatch("1")));
        return findAll(Pageable.create(0, MAX_PLANS, Sort.by("name")),
                       b -> b.query(composeFilter(termFilter("archived", false), offeredTo)))
                .map(Page::getContent);
    }
}
