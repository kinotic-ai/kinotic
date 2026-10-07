package org.kinotic.domain.api.services.security.authorization;

import io.vertx.core.Future;
import org.kinotic.idl.api.annotations.PermissionNamespace;
import org.kinotic.idl.api.annotations.RequirePermission;
import org.kinotic.idl.api.annotations.ResourceTarget;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.domain.api.model.security.authorization.*;

/** Manages access within the authenticated organization's applications and tenants. */
@Publish
@PermissionNamespace("access")
@ResourceTarget(type = "authorization")
@RequirePermission(value = "manage", tenantDelegable = true)
@Zone("app-api")
@Version("1.0.0")
public interface AccessControlService {
    Future<AuthorizationPolicyView> load(AuthorizationScope scope);
    Future<AuthorizationPolicyView> save(AuthorizationPolicy policy, long expectedRevision);
    Future<AuthorizationPolicyView> initialize(AuthorizationScope scope, String administratorIdentityId);
    Future<AuthorizationPolicyView> republish(AuthorizationScope scope, long expectedRevision);
    Future<org.kinotic.core.api.crud.Page<AuthorizationIdentityOption>> findIdentities(AuthorizationScope scope, org.kinotic.core.api.crud.Pageable pageable);
    Future<Void> publishContracts(String applicationId, java.util.List<ApplicationServiceContract> contracts);
}
