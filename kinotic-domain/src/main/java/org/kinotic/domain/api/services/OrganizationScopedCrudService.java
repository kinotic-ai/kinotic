package org.kinotic.domain.api.services;

import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.IdentifiableCrudService;

/**
 * An {@link IdentifiableCrudService} whose entities belong to an organization. Every read, write and
 * delete is held to the organization of the authenticated participant: a read answers with that
 * organization's entities only, a write must name it, and a participant of another scope is refused.
 * The organization is never a parameter, so the contract is the same shape as the unscoped service
 * and differs only in what a call may reach.
 *
 * @param <T>  the entity type
 * @param <ID> the id type
 */
public interface OrganizationScopedCrudService<T extends Identifiable<ID>, ID> extends IdentifiableCrudService<T, ID> {
}
