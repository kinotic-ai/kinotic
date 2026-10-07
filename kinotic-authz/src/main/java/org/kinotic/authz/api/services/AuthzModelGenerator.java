package org.kinotic.authz.api.services;

import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.Collection;

/**
 * Generates a store's authorization model from the services it serves. The model is a pure function of its
 * inputs, so two calls with the same declarations produce the same definition and hash.
 *
 * Every model carries the kernel: {@code user}, {@code group}, {@code role} and {@code role_binding}, plus the
 * membership relations of the kinds of store, and an application's model the fixed types of its data,
 * {@code entity_definition} for a definition's rows and {@code tenant_definition} for those rows within one
 * tenant. Each service declaring an {@code @AuthzResource} contributes its type, its parent and the permissions
 * its functions require, the entities repository among them, so a definition's rows have the permissions it
 * declares. A permission is named {@code <type>_<permission>} throughout, it is
 * held by a binding of a role that bundles it on the resource or on any ancestor, and the derived
 * {@code can_delete}, {@code can_edit} and {@code can_view} imply each other in that order.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface AuthzModelGenerator {

    /**
     * The model of the platform store, from the platform's own services.
     *
     * @param services the converted contracts of every service the platform publishes
     * @return the model
     * @throws IllegalArgumentException when a declaration names a type the model does not have, a parent that
     *                                  would cycle, or a permission whose model name is too long
     */
    AuthzModel platformModel(Collection<ServiceDefinition> services);

    /**
     * The model of one application's store, from the platform's services its users call and the application's
     * own. Of the platform's services only those declared on the tenant and on the entity definition take
     * part: the ones serving a tenant's users in every application, whose permissions the tenant then carries,
     * and the entities repository, whose permissions a definition's rows have; a platform service declared on
     * any other type stays out of the application's model. The model does not depend on the application's
     * definitions: every definition's rows are typed {@code entity_definition}, and the definition itself is
     * the object a check or a grant names.
     *
     * @param platformServices the converted contracts of the platform's own services
     * @param services         the converted contracts of the application's own services
     * @return the model
     * @throws IllegalArgumentException as for {@link #platformModel}
     */
    AuthzModel applicationModel(Collection<ServiceDefinition> platformServices, Collection<ServiceDefinition> services);

}
