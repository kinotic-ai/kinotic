package org.kinotic.authz.api.model;

/**
 * Who a grant is made to: a user by its identity id, or a group or a tenant by its id, which stands for every
 * member the group, or every user the tenant, has at the time of a check.
 *
 * @param type whether the id names a user, a group or a tenant
 * @param id   the user's identity id, the group's id or the tenant's id
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record Subject(SubjectType type, String id) {
}
