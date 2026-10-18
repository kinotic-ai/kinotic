package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OidcBindingRepository extends AbstractRepository<OidcBinding> {

    // the most provider identities one user signs in with
    private static final int MAX_BINDINGS = 50;

    public OidcBindingRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_binding", OidcBinding.class, crudServiceTemplate);
    }

    /** The bindings of user {@code identityId}, oldest first. */
    public Future<List<OidcBinding>> findByIdentity(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return findAll(Pageable.ofSize(MAX_BINDINGS), b -> b.query(termFilter("identityId", identityId))
                                                             .sort(s -> s.field(f -> f.field("created"))))
                .map(Page::getContent);
    }
}
