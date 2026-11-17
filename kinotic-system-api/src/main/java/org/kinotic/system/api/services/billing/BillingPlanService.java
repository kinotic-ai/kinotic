package org.kinotic.system.api.services.billing;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.billing.BillingPlan;

/**
 * The {@link BillingPlan}s organizations subscribe to, which system users offer. A plan never changes once created,
 * since the subscriptions on it are billed with its Stripe Prices: a new price is a new plan, and a plan no longer
 * offered is archived.
 * <p>
 * Published in the system zone, which only SYSTEM participants and the system's own cluster members may address.
 */
@Publish
public interface BillingPlanService {

    /** Every plan, archived ones included, a page at a time. */
    Future<Page<BillingPlan>> findAll(Pageable pageable);

    /** The plan {@code id}, or {@code null} when none has that id. */
    Future<BillingPlan> findById(String id);

    /**
     * Creates a plan and its Stripe Product, with a Stripe Price for each component.
     *
     * @param plan the plan: a name, a three-letter lowercase currency, an interval and at least one component. A
     *             {@code FLAT} component has an amount; a {@code METERED} component has a meter, used by no other
     *             component of the plan, and a unit amount. Amounts are in the currency's smallest unit and not
     *             negative. With an organization id, the plan is offered to that organization alone
     * @return the plan as created, with its id and the ids of its Stripe objects
     */
    Future<BillingPlan> create(BillingPlan plan);

    /**
     * Stops offering the plan {@code id}: no organization can subscribe to it from now on, and the subscriptions on it
     * go on as they are. Nothing happens for a plan already archived.
     *
     * @return the plan as archived; fails when no plan has the id
     */
    Future<BillingPlan> archive(String id);
}
