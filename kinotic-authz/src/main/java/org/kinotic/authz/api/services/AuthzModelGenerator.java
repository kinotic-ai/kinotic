package org.kinotic.authz.api.services;

import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.ServiceModelNames;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.Collection;
import java.util.Set;

/**
 * Generates a store's authorization model from the services it serves. The model is a pure function of its
 * inputs, so two calls with the same declarations produce the same definition and hash.
 *
 * Every model carries the kernel: {@code user} and {@code group}, plus the membership relations of the kinds of
 * store, and for each resource type {@code <type>} a role type {@code <type>_role}, whose roles bundle only the
 * permissions of the type and of the types inside it, and a binding type {@code <type>_role_binding}, the only
 * binding a resource of the type accepts, which grants only roles homed at the type or inside it within its trust
 * boundary (the system's store bounds an organization and a tenant). An application's model also carries the
 * fixed types of its data, {@code entity_definition} for a definition's rows and {@code tenant_definition} for
 * those rows within one tenant. Each service declaring an {@code @AuthzResource} contributes its type, its parent and the permissions
 * its functions require, the entities repository among them, so a definition's rows have the permissions it
 * declares. A permission is named {@code <type>_<permission>} throughout, it is
 * held by a binding of a role that bundles it on the resource or on any ancestor, and the derived
 * {@code can_delete}, {@code can_edit} and {@code can_read} imply each other in that order. The types of an
 * application's own services are names in its model's catalog alone: the engine's definition of an application's
 * model is the same for any number of them.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface AuthzModelGenerator {

    /**
     * The resource types an application's store places a resource of: the application, its tenants and its entity
     * definitions. Any other type in the store is one of the application's own services, which the store holds no
     * resource of.
     */
    Set<String> APPLICATION_TYPES = Set.of(AuthzConstants.APPLICATION_TYPE, AuthzConstants.TENANT_TYPE, AuthzConstants.ENTITY_DEFINITION_TYPE);

    /**
     * The model of the system store, from the system's own services.
     *
     * @param services the converted contracts of every service the system publishes
     * @return the model
     * @throws IllegalArgumentException when a declaration names a type the model does not have, a parent that
     *                                  would cycle, or a permission whose model name is too long
     */
    AuthzModel systemModel(Collection<ServiceDefinition> services);

    /**
     * The model of one application's store, from the system's services its users call and the application's
     * own. Of the system's services only those declared on the tenant and on the entity definition take
     * part: the ones serving a tenant's users in every application, whose permissions the tenant then carries,
     * and the entities repository, whose permissions a definition's rows have; a system service declared on
     * any other type stays out of the application's model. The model does not depend on the application's
     * definitions: every definition's rows are typed {@code entity_definition}, and the definition itself is
     * the object a check or a grant names. A type of the application's own services adds its permissions and
     * roles to the model's catalog and nothing to its definition, unless it is one of
     * {@link #APPLICATION_TYPES}: its permissions are {@link AuthzModel#servicePermissions()}.
     *
     * @param systemServices the converted contracts of the system's own services
     * @param services         the converted contracts of the application's own services
     * @return the model
     * @throws IllegalArgumentException as for {@link #systemModel}
     */
    AuthzModel applicationModel(Collection<ServiceDefinition> systemServices, Collection<ServiceDefinition> services);

    /**
     * What a service's contract names of a model: the types it decides, the parent of the type it declares, and
     * the permissions its functions require.
     *
     * @param service the converted contract of a service
     * @return the names, none for a service that declares no type and checks nothing
     */
    ServiceModelNames namesOf(ServiceDefinition service);

}
