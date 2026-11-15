package org.kinotic.system.internal.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.billing.BillingAccount;
import org.springframework.stereotype.Component;

@Component
public class BillingAccountRepository extends AbstractRepository<BillingAccount> {

    public BillingAccountRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_billing_account", BillingAccount.class, crudServiceTemplate);
    }
}
