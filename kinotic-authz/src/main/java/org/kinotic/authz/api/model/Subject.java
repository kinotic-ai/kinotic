package org.kinotic.authz.api.model;

/**
 * Who a grant is made to: a user by its identity id, or a group, a tenant or an application by its id, which stands
 * for every member the group, every user the tenant, or every end user the application, has at the time of a check.
 *
 * @param type whether the id names a user, a group, a tenant or an application
 * @param id   the user's identity id, the group's id, the tenant's id or the application's id
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record Subject(SubjectType type, String id) {
}
