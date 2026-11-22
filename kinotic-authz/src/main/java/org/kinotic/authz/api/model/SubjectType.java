package org.kinotic.authz.api.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.utils.AuthzConstants;

/**
 * What a {@link Subject} is: one user, every member of a group, every user of a tenant, or every end user of an
 * application.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
@Getter
@RequiredArgsConstructor
public enum SubjectType {
    USER(AuthzConstants.USER_TYPE, null),
    GROUP(AuthzConstants.GROUP_TYPE, AuthzConstants.MEMBER_RELATION),
    TENANT(AuthzConstants.TENANT_TYPE, AuthzConstants.MEMBER_RELATION),
    APPLICATION(AuthzConstants.APPLICATION_TYPE, AuthzConstants.END_USER_RELATION);

    /**
     * The type of the object a subject of this type is named by in a store.
     */
    private final String objectType;

    /**
     * The relation of that object whose holders a subject of this type stands for, or null for a user, which
     * stands for itself.
     */
    private final String relation;
}
