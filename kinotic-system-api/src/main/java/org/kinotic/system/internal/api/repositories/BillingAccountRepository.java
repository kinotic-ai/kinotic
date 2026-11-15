package org.kinotic.system.internal.api.repositories;

import co.elastic.clients.elasticsearch.core.get.GetResult;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class BillingAccountRepository extends AbstractRepository<BillingAccount> {

    public BillingAccountRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_billing_account", BillingAccount.class, crudServiceTemplate);
    }

    /**
     * Applies {@code change} to the account with the given id and saves the account it returns, provided no other
     * write landed on the account since it was read.
     *
     * @param id     the id of an existing account
     * @param change the change to the account as read
     * @return the account saved, or {@code null} when another write landed on the account first
     */
    public Future<BillingAccount> update(String id, Function<BillingAccount, Future<BillingAccount>> change) {
        return crudServiceTemplate.findById(indexName, id, type, null, Function.<GetResult<BillingAccount>>identity())
                .compose(read -> {
                    Validate.validState(read.found() && read.source() != null, "No billing account has the id %s", id);
                    return change.apply(read.source())
                                 .compose(changed -> crudServiceTemplate.save(indexName, id, changed,
                                                                              b -> b.ifSeqNo(read.seqNo())
                                                                                    .ifPrimaryTerm(read.primaryTerm()))
                                                                        .map(changed)
                                                                        .recover(error -> CrudServiceTemplate.isVersionConflict(error)
                                                                                ? Future.succeededFuture(null)
                                                                                : Future.failedFuture(error)));
                });
    }
}
