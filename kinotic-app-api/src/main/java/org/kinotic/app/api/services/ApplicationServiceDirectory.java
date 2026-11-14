package org.kinotic.app.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzRole;
import org.kinotic.idl.api.utils.AuthzConstants;

/**
 * Where an application's runtimes register the services they serve in the platform's service directory, so
 * the directory lists them beside the platform's own: a runtime registers each service as it comes online.
 * The application must belong to the caller's organization, the caller must hold the permission to register the
 * application's services, which the {@link AuthzConstants#APPLICATION_RUNTIME_ROLE runtime} role of each of its
 * deployments bundles, and the entry's zone must be the application's.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzConstants.APPLICATION_TYPE, parent = AuthzConstants.ORGANIZATION_TYPE,
               roles = @AuthzRole(id = AuthzConstants.APPLICATION_RUNTIME_ROLE, permissions = AuthzConstants.CAN_REGISTER_SERVICES))
public interface ApplicationServiceDirectory {

    /**
     * Registers a service a runtime of the application serves, replacing the entry stored for the service when
     * it differs. The entry names the application, the service's zone and version, whether it is advertised,
     * and its definition with the decorators it declares; the platform owns everything else about the entry.
     *
     * @param entry the service to register, in the application's zone
     * @return a future that completes once the entry is in the directory
     */
    @AuthzCheck(permission = AuthzConstants.CAN_REGISTER_SERVICES, resourceId = "{entry.applicationId}")
    Future<Void> register(ServiceDirectoryEntry entry);

}
