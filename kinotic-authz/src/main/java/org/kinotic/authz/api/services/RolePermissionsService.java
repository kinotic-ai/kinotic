package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.model.RolePermissions;

/**
 * What the roles of a store bundle: the permissions a grant of each role gives. Every operation names its store the
 * way the store's record is named, {@link AuthzStoreService#SYSTEM} for the system's and an application's id for
 * its own.
 */
public interface RolePermissionsService {

    /**
     * The permissions a store holds for a role, which a grant of the role gives.
     *
     * @param store  the store, named as its record is
     * @param roleId the role, {@code <home type>.<name>}
     * @return the permissions, each set in its natural order; empty for a role the store holds none for
     */
    Future<RolePermissions> findRolePermissions(String store, String roleId);

    /**
     * Makes one role of the store bundle exactly the given permissions: a permission the role lacks is added, one it
     * holds beyond the given ones is removed, and a role already in step is left as it is. A permission whose
     * definitions change is held for none of them from the moment the change starts until it completes.
     *
     * @param store       the store, named as its record is
     * @param roleId      the role, {@code <home type>.<name>}
     * @param permissions what the role must bundle; a permission bundled for everything is not also bundled for a
     *                    definition
     * @return completes when the role is in step; fails with the engine's validation error for a permission the
     *         store's model does not let the role bundle that way
     */
    Future<Void> ensureRole(String store, String roleId, RolePermissions permissions);

    /**
     * Makes the store run the given model with the built-in roles it holds, {@link AuthzModel#storedRoles()},
     * bundling exactly what the model has. The roles are brought in step before the model becomes the store's
     * current version, so a check against the model reads every role it grants through as the model needs it.
     *
     * @param store the store, named as its record is
     * @param model the model the store must run
     * @return the engine's ids of the store, with the version it now runs, for the store's record to name
     */
    Future<EngineStore> ensureModelWithRoles(String store, AuthzModel model);
}
