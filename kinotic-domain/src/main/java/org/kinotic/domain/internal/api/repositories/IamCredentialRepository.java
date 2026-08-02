package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class IamCredentialRepository extends AbstractRepository<IamCredential> {

    public IamCredentialRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_iam_credential", IamCredential.class, crudServiceTemplate);
    }
}

