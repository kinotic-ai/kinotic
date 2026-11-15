package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationLoginLink;
import java.util.Collection;
import java.util.List;

public interface OrganizationService extends IdentifiableCrudService<Organization, String> {

    /** The organizations among {@code ids} that exist, in one read. */
    Future<List<Organization>> findAllById(Collection<String> ids);

    /**
     * Emails the person at {@code email} the organizations the address is an enabled member of, each with a link
     * to that organization's login, for someone who does not remember where to sign in. The email is
     * sent whether or not the address is a member of any organization, so the caller learns nothing
     * about the address.
     * <p>
     * When {@code kinotic.domain.email.enabled} is {@code false}, no email is sent and the links are returned for
     * the UI to show, so anyone who asks learns which organizations the address is a member of.
     *
     * @param email the address to send to
     * @return a future emitting the organizations' login links when the UI shows them, possibly none, or
     *         {@code null} once the email has been sent
     */
    Future<List<OrganizationLoginLink>> sendOrganizationsEmail(String email);

    /**
     * Deactivates the organization: its microservices are stopped and its sites show that it is deactivated, until
     * {@link #reactivate(String)}.
     *
     * @param organizationId the organization
     * @return a future emitting true when this call deactivated it, false when it was deactivated already
     */
    Future<Boolean> deactivate(String organizationId);

    /**
     * Reactivates a deactivated organization, whose microservices start again and whose sites are served again.
     *
     * @param organizationId the organization
     * @return a future emitting true when this call reactivated it, false when it was active already
     */
    Future<Boolean> reactivate(String organizationId);

}
