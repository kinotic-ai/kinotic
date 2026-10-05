package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzRole;
import org.kinotic.idl.api.directory.ServiceContract;
import org.kinotic.idl.api.utils.AuthzUtil;

/**
 * Where an application's runtimes publish the contracts of the services they serve, so the platform checks
 * every request to them against the application's store: a runtime registers each service that declares a
 * resource as it connects. The application must belong to the caller's organization and the contract's zone
 * must be the application's, and a contract that does not convert, for a function whose check cannot be
 * derived or a template naming a parameter the function lacks, is refused.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzUtil.APPLICATION_TYPE,
               parent = AuthzUtil.ORGANIZATION_TYPE,
               roles = @AuthzRole(id = ServiceContractService.RUNTIME_ROLE, permissions = ServiceContractService.CAN_PUBLISH_SERVICES))
public interface ServiceContractService {

    /**
     * The permission to publish a service of an application, on the application.
     */
    String CAN_PUBLISH_SERVICES = "can_publish_services";

    /**
     * The role of an application's runtimes, which a deployment grants the runtime machines it provisions on
     * the application.
     */
    String RUNTIME_ROLE = AuthzUtil.APPLICATION_TYPE + ".runtime";

    /**
     * Publishes the contract of a service a runtime of the application serves, replacing the one stored for
     * the service when it differs.
     *
     * @param applicationId the application the service belongs to
     * @param contract      the service's declared contract, in the application's zone
     * @return a future that completes once the contract is in the directory
     */
    @AuthzCheck(permission = CAN_PUBLISH_SERVICES)
    Future<Void> register(String applicationId, ServiceContract contract);

}
