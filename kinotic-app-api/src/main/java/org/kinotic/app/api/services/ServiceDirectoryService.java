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
import org.kinotic.idl.api.utils.AuthzUtil;

/**
 * Where an application's runtimes register the services they serve in the platform's service directory, so
 * the directory lists them beside the platform's own and every request to a service declaring a resource is
 * checked against the application's store: a runtime registers each service as it comes online. The
 * application must belong to the caller's organization and the entry's zone must be the application's, and a
 * definition that does not derive, for a function whose check cannot be derived or a template naming a
 * parameter the function lacks, is refused.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzUtil.APPLICATION_TYPE,
               parent = AuthzUtil.ORGANIZATION_TYPE,
               roles = @AuthzRole(id = AuthzUtil.APPLICATION_RUNTIME_ROLE, permissions = ServiceDirectoryService.CAN_REGISTER_SERVICES))
public interface ServiceDirectoryService {

    /**
     * The permission to register a service of an application in the directory, on the application.
     */
    String CAN_REGISTER_SERVICES = "can_register_services";

    /**
     * Registers a service a runtime of the application serves, replacing the entry stored for the service when
     * it differs. The entry names the application, the service's zone and version, whether it is advertised,
     * and its definition with the decorators it declares; the platform derives the checks of a service
     * declaring a resource and owns everything else about the entry.
     *
     * @param entry the service to register, in the application's zone
     * @return a future that completes once the entry is in the directory
     */
    @AuthzCheck(permission = CAN_REGISTER_SERVICES, objectId = "{entry.applicationId}")
    Future<Void> register(ServiceDirectoryEntry entry);

}
