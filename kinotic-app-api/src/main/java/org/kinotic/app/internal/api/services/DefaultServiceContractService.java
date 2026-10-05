package org.kinotic.app.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.app.api.services.ServiceContractService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.directory.ServiceContract;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultServiceContractService implements ServiceContractService {

    private final SecurityContext securityContext;
    private final ApplicationRepository applications;
    private final ObjectProvider<ServiceDirectory> directoryProvider;

    @Override
    public Future<Void> register(String applicationId, ServiceContract contract) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notNull(contract, "contract cannot be null");
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        String zone = DomainUtil.applicationZone(organizationId, applicationId);
        // a service of the application is addressed in the application's zone, or in one of its own labels under it
        Validate.isTrue(zone.equals(contract.zone()) || (contract.zone() != null && contract.zone().startsWith(zone + ".")),
                        "The contract of %s is declared in the zone '%s', which is not the application's", contract.qualifiedName(), contract.zone());
        ServiceDirectory directory = directoryProvider.getIfAvailable();
        Validate.validState(directory != null, "This server holds no service directory to publish a contract in");
        return applications.findById(applicationId, organizationId)
                           .compose(application -> {
                               DomainUtil.requireOwned(application, organizationId, "No application of the organization has id " + applicationId);
                               return directory.registerContract(contract, organizationId, applicationId);
                           })
                           .onSuccess(v -> log.info("Published the contract of {} for application {}", contract.qualifiedName(), applicationId));
    }
}
