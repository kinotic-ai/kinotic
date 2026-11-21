package org.kinotic.authz.api.model;

/**
 * What a {@link Subject} is: one user, every member of a group, or every user of a tenant.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public enum SubjectType {
    USER,
    GROUP,
    TENANT
}
