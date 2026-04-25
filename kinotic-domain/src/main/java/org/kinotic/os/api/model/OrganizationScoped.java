package org.kinotic.os.api.model;

import org.kinotic.core.api.crud.Identifiable;

/**
 * This interface is used to mark entities that are owned by an organization.
 * And will be automatically filtered by organizationId
 * Created By Navíd Mitchell 🤪on 4/12/26
 */
public interface OrganizationScoped<T> extends Identifiable<T> {

    String getOrganizationId();

    void setOrganizationId(String organizationId);

}
