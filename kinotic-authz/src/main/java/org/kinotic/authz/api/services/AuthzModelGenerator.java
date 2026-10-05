package org.kinotic.authz.api.services;

import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.EntityResource;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.Collection;

/**
 * Generates a store's authorization model from the services and entity definitions it serves. The model is a
 * pure function of its inputs, so two calls with the same declarations produce the same definition and hash.
 *
 * Every model carries the kernel: {@code user}, {@code group}, {@code role} and {@code role_binding}, plus the
 * membership relations of the kinds of store. Each service declaring an {@code @AuthzResource} contributes its
 * type, its parent and the permissions its functions require, and each entity definition contributes a type for
 * its rows. A permission is named {@code <type>_<permission>} throughout, it is held by a binding of a role that
 * bundles it on the resource or on any ancestor, and the derived {@code can_delete}, {@code can_edit} and
 * {@code can_view} imply each other in that order.
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
     * The model of one application's store, from the platform's services its users call, the services serving
     * its data and its own entity definitions. Of the platform's services only those declared on the tenant
     * take part, the ones serving a tenant's users in every application, whose permissions the tenant then
     * carries; a platform service declared on any other type stays out of the application's model.
     *
     * @param platformServices the converted contracts of the platform's own services
     * @param services         the converted contracts of the application's own services
     * @param entities         the application's entity definitions
     * @return the model
     * @throws IllegalArgumentException as for {@link #platformModel}, or when an entity's type collides with a
     *                                  service's
     */
    AuthzModel applicationModel(Collection<ServiceDefinition> platformServices,
                                Collection<ServiceDefinition> services,
                                Collection<EntityResource> entities);

}
