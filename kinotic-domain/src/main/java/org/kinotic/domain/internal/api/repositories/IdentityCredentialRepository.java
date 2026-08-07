package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.internal.api.model.IdentityCredential;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdentityCredentialRepository extends AbstractRepository<IdentityCredential> {

    public IdentityCredentialRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_identity_credential", IdentityCredential.class, crudServiceTemplate);
    }
}

