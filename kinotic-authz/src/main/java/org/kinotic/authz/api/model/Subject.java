package org.kinotic.authz.api.model;

/**
 * Who a grant is made to: a user by its identity id, or a group by its id, which stands for every member the
 * group has at the time of a check.
 *
 * @param kind whether the id names a user or a group
 * @param id   the user's identity id or the group's id
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record Subject(SubjectKind kind, String id) {
}
