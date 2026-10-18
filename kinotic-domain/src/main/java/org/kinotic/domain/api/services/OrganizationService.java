package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Organization;
import java.util.Collection;
import java.util.List;

public interface OrganizationService extends IdentifiableCrudService<Organization, String> {

    /** The organizations among {@code ids} that exist, in one read. */
    Future<List<Organization>> findAllById(Collection<String> ids);

    /**
     * Emails the person at {@code email} the organizations the address is a member of, each with a link
     * to that organization's login, for someone who does not remember where to sign in. The email is
     * sent whether or not the address is a member of any organization, so the caller learns nothing
     * about the address.
     *
     * @param email the address to send to
     * @return a future that completes once the email has been sent
     */
    Future<Void> sendMembershipsEmail(String email);

}
