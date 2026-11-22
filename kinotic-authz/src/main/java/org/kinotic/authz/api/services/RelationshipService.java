package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.RolePermissions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The relationships held in the stores, and the questions a store answers from them. Every operation names its
 * store the way the store's record is named, {@link AuthzStoreService#SYSTEM} for the system's and an
 * application's id for its own, and the module resolves the name to the engine's store.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface RelationshipService {

    /**
     * The permissions a store holds for a role, which a grant of the role gives.
     *
     * @param store  the store, named as its record is
     * @param roleId the role, {@code <home type>.<name>}
     * @return the permissions, each set in its natural order; empty for a role the store holds none for
     */
    Future<RolePermissions> findRolePermissions(String store, String roleId);

    /**
     * The role bindings a role or a subject is part of: one per resource the role is granted on, or one per grant
     * made to the subject.
     *
     * @param store   the store, named as its record is
     * @param user    the role or the subject as a binding holds it: {@code <type>_role:<id>}, {@code user:<id>} or
     *                {@code group:<id>#member}
     * @param boundOn the types of the resources the bindings are attached to
     * @return the bindings, each in {@code type:id} form
     */
    Future<List<String>> findBindings(String store, String user, Set<String> boundOn);

    /**
     * The members of a group or of a role binding.
     *
     * @param store  the store, named as its record is
     * @param object the group or the binding, in {@code type:id} form
     * @return the members as the store holds them: {@code user:<id>}, or {@code group:<id>#member} for a group
     *         granted the binding's role
     */
    Future<List<String>> findMembers(String store, String object);

    /**
     * An object and each object containing it, read upward to a given ancestor or to the root of the object's tree.
     *
     * @param store   the store, named as its record is
     * @param object  the object, in {@code type:id} form
     * @param upTo    the ancestor the read stops at
     * @param parents for each type contained in another, the type containing it, as {@link AuthzModel#parents} has it
     * @return the object first, then each ancestor in order, ending with {@code upTo} when the object is inside it
     */
    Future<List<String>> findLineage(String store, String object, String upTo, Map<String, String> parents);

    /**
     * Whether the store holds exactly this relationship, as written, without the model's rules.
     *
     * @param store        the store, named as its record is
     * @param relationship the user, relation and object
     * @return true when the relationship is held
     */
    Future<Boolean> holds(String store, RelationshipTuple relationship);

    /**
     * Adds the relationships the store does not hold yet, so a write made again after a failure adds nothing
     * twice.
     *
     * @param store         the store, named as its record is
     * @param relationships the relationships that must be held
     * @return completes when every one is held
     */
    Future<Void> ensure(String store, List<RelationshipTuple> relationships);

    /**
     * Removes the relationships the store holds, leaving the ones it does not alone.
     *
     * @param store         the store, named as its record is
     * @param relationships the relationships that must not be held
     * @return completes when none is held
     */
    Future<Void> remove(String store, List<RelationshipTuple> relationships);

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

    /**
     * Grants a role to a member on a resource: the member joins the one binding the role has on the resource,
     * through which it holds every permission the role bundles on the resource and on everything inside it.
     * A grant made again, after a failure or by another server at the same time, adds nothing twice. The engine
     * refuses a role whose home the resource's type may not grant.
     *
     * @param store  the store, named as its record is
     * @param roleId the role granted, {@code <home type>.<name>}, such as a built-in role's id
     * @param member who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     * @param object the resource the grant is made on, in {@code type:id} form
     * @return the grant's id, {@link AuthzNameUtil#grantId}, which {@link #revoke} revokes it by; fails with the
     *         engine's validation error when the resource's type may not grant the role
     */
    Future<String> bind(String store, String roleId, String member, String object);

    /**
     * Takes a resource out of a store: every grant made on it, and every relationship on it, such as its place
     * under its parent and its members. A resource created again under the same id starts with no grant.
     *
     * @param store  the store, named as its record is
     * @param object the resource, in {@code type:id} form
     * @return completes when the store holds nothing of the resource
     */
    Future<Void> removeObject(String store, String object);

    /**
     * Takes out of a store every resource of a type that the given user is related to, each as
     * {@link #removeObject(String, String)} does.
     *
     * @param store the store, named as its record is
     * @param user  the user the resources are related to, in {@code type:id} form
     * @param type  the type of the resources
     * @return completes when the store holds nothing of the resources
     */
    Future<Void> removeObjectsOf(String store, String user, String type);

    /**
     * Takes a user, or the members of a group or a tenant as a whole, out of a store: every grant made to it, and
     * every relationship it holds, such as its memberships. A user created again under the same id starts with no
     * grant.
     *
     * @param store the store, named as its record is
     * @param user  the user, in {@code type:id} form, or the members, in {@code type:id#member} form
     * @return completes when the store holds nothing of the user, at once for a store that does not exist
     */
    Future<Void> removeSubject(String store, String user);

    /**
     * Runs changes to an object's record and its place in the stores while no other caller of this function is
     * changing the same object anywhere in the cluster, so a creation and a deletion of one id, each made of
     * several writes, never interleave. Objects of one type may wait on each other.
     *
     * @param object  the object, in {@code type:id} form
     * @param changes the changes to run, which may call this function for an object of another type and never for
     *                one of the same type, since the two may wait on each other
     * @return what the changes complete with; fails when the object is still being changed by another caller
     *         after thirty seconds
     */
    <T> Future<T> exclusively(String object, Supplier<Future<T>> changes);

    /**
     * Creates the record an object stands for, with its place in the stores, while no other caller changes the
     * object, as {@link #exclusively} runs it. A record stored already refuses the create. What a deleted record
     * of the object left in the stores is removed before the new record is written, so the new record is never
     * reached through a grant made on the old one.
     *
     * @param object the object the record is known by in the stores, in {@code type:id} form
     * @param stored reads the record stored for the object, emitting null when there is none
     * @param clear  removes what a deleted record of the object left behind
     * @param create writes the record and places it in the stores; what becomes of a record that cannot be placed
     *               is its to decide
     * @return a future emitting what the create emits, or failing with an
     *         {@link org.kinotic.core.api.exceptions.AlreadyExistsException} when a record is stored for the object
     */
    <T> Future<T> createExclusively(String object, Supplier<Future<T>> stored, Supplier<Future<Void>> clear, Supplier<Future<T>> create);

    /**
     * Revokes a grant where it was made: its member no longer holds its role on the object, and everyone else
     * granted the role there still does. A grant made on another object is left as it is.
     *
     * @param store   the store, named as its record is
     * @param grantId the grant's id, the one {@link #bind} returned
     * @param object  the resource the grant was made on, in {@code type:id} form
     * @return completes when the grant is no longer held; fails with {@link IllegalArgumentException} when the id
     *         is not a grant's, or no such grant was made on the object
     */
    Future<Void> revoke(String store, String grantId, String object);

    /**
     * The grants made on a resource: one for each member of each role bound on it that is a subject of a grant, a
     * user, a group's members, a tenant's users or an application's end users. A member that is any other userset
     * is not a grant and is left out.
     *
     * @param store  the store, named as its record is
     * @param object the resource, in {@code type:id} form
     * @return the grants, empty for a resource nothing is bound on
     */
    Future<List<Grant>> findGrants(String store, String object);

    /**
     * Whether a subject holds a permission on an object, and which of the given grants give it: a grant gives it
     * when its role bundles the permission or one implying it, and it was made to the subject, or to a group, a
     * tenant or an application whose members, users or end users the subject is one of. The answer reflects every
     * write that completed before the call.
     *
     * @param store   the store, named as its record is
     * @param model   the model the store runs
     * @param holds   the subject asked about, as {@code user:<id>} or {@code group:<id>#member}, the model name of
     *                the permission and the object
     * @param context tuples held for this check only, as {@link #check(String, RelationshipTuple, Consistency, List)} takes them
     * @param grants  the grants that reach the object
     * @return whether the permission is held, and the grants giving it as the store holds them
     */
    Future<AccessExplanation> explain(String store,
                                      AuthzModel model,
                                      RelationshipTuple holds,
                                      List<RelationshipTuple> context,
                                      List<Grant> grants);

    /**
     * Whether the user holds the relation on the object, directly or through the rules of the model the store runs.
     *
     * @param store        the store, named as its record is
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @return true when the relation is held
     */
    default Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency) {
        return check(store, relationship, consistency, List.of());
    }

    /**
     * Whether the user holds the relation on the object, with the given tuples taken as held for this check
     * alone, beside the store's: the edges of an object the store keeps no tuples for.
     *
     * @param store        the store, named as its record is
     * @param relationship the user, relation and object to check
     * @param consistency  how current the answer must be
     * @param context      tuples held for this check only
     * @return true when the relation is held; false when the store's newest model has no such relation or type
     */
    Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context);

    /**
     * Whether a subject holds a permission of an application's own services on a tenant or on the application,
     * through a role granted there or above: one that holds every such permission, one of the application's own
     * naming this one, or one of the given roles.
     *
     * @param store       the application's store
     * @param user        the subject, in {@code type:id} form
     * @param permission  the model name of the permission, such as {@code report_can_generate}
     * @param object      the tenant or the application, in {@code type:id} form
     * @param roles       the ids of the roles holding the permission by the contracts of the application's
     *                    services, as {@link org.kinotic.authz.api.model.AuthzModel#rolesHolding} gives them
     * @param consistency whether the answer may come from the engine's caches
     * @return true when the subject holds the permission on the object
     */
    Future<Boolean> checkServicePermission(String store, String user, String permission, String object, Set<String> roles, Consistency consistency);

    /**
     * The objects of a type on which the user holds the relation, by the rules of the model the store runs. The
     * future fails with an {@link IllegalStateException} when the engine's listing deadline, three seconds, ended
     * the listing, which may then be missing objects.
     *
     * @param store       the store, named as its record is
     * @param user        who holds the relation
     * @param relation    the relation held
     * @param type        the type of object to list
     * @param consistency how current the answer must be
     * @return the objects, in {@code type:id} form; none when the store's newest model has no such relation or type
     */
    Future<List<String>> listObjects(String store, String user, String relation, String type, Consistency consistency);
}
