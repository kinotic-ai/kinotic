package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The relationships held in the stores: the tuples placing and relating objects, and the grants made through the
 * bindings of roles. Every operation names its store the way the store's record is named,
 * {@link AuthzStoreService#SYSTEM} for the system's and an application's id for its own, and the module resolves the
 * name to the engine's store.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface RelationshipService {

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
}
