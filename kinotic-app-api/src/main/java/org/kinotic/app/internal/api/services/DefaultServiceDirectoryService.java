package org.kinotic.app.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.app.api.services.ServiceDirectoryService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultServiceDirectoryService implements ServiceDirectoryService {

    private final SecurityContext securityContext;
    private final ApplicationRepository applications;
    private final ObjectProvider<ServiceDirectory> directoryProvider;

    @Override
    public Future<Void> register(ServiceDirectoryEntry entry) {
        Validate.notNull(entry, "entry cannot be null");
        Validate.notBlank(entry.getApplicationId(), "The entry names no application");
        Validate.notNull(entry.getServiceDefinition(), "The entry carries no service definition");
        String applicationId = entry.getApplicationId();
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        String zone = DomainUtil.applicationZone(organizationId, applicationId);
        // a service of the application is addressed in the application's zone, or in one of its own labels under it
        Validate.isTrue(zone.equals(entry.getZone()) || (entry.getZone() != null && entry.getZone().startsWith(zone + ".")),
                        "The service %s is declared in the zone '%s', which is not the application's",
                        entry.getServiceDefinition().getQualifiedName(), entry.getZone());
        ServiceDirectory directory = directoryProvider.getIfAvailable();
        Validate.validState(directory != null, "This server holds no service directory to register a service in");
        // the organization is the caller's, whatever the entry names
        entry.setOrganizationId(organizationId);
        return applications.findById(applicationId, organizationId)
                           .compose(application -> {
                               DomainUtil.requireOwned(application, organizationId, "No application of the organization has id " + applicationId);
                               return directory.register(entry);
                           })
                           .onSuccess(v -> log.info("Registered the service {} of application {} in the directory",
                                                    entry.getServiceDefinition().getQualifiedName(), applicationId));
    }
}
