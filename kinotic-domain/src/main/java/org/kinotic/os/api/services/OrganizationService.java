package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.Organization;

@Publish
public interface OrganizationService extends IdentifiableCrudService<Organization, String> {
}
