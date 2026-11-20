package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import org.kinotic.authz.api.model.RolePermissions;

import java.util.List;

/**
 * What a store holds of a role.
 *
 * @param permissions what the role bundles
 * @param partTuples  the tuples of the role's parts and the tuples the role holds them by; deleting them leaves
 *                    the role naming no definition
 */
record HeldRole(RolePermissions permissions, List<TupleKeyWithoutCondition> partTuples) {
}
