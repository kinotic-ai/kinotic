/**
 * What a {@link Subject} is: one user, every member of a group, every user of a tenant, or every end user of an
 * application.
 */
export enum SubjectType {
    USER = 'USER',
    GROUP = 'GROUP',
    TENANT = 'TENANT',
    APPLICATION = 'APPLICATION'
}
