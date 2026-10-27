package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.PendingApplicationSignUp;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * Persistence for {@link PendingApplicationSignUp} records: email lookup within one application plus the token-based,
 * single-use, expiry-aware consumption inherited from {@link AbstractTokenVerificationRepository}.
 */
@Component
public class PendingApplicationSignUpRepository extends AbstractTokenVerificationRepository<PendingApplicationSignUp> {

    public PendingApplicationSignUpRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_pending_application_sign_up", PendingApplicationSignUp.class, crudServiceTemplate);
    }

    /** The sign-up of {@code email}, normalized, to the application, expired or not, or {@code null} when none is pending. */
    public Future<PendingApplicationSignUp> findByEmail(String email, String organizationId, String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                termFilter("organizationId", organizationId),
                termFilter("applicationId", applicationId))));
    }
}
