/**
 * What a {@link Subject} is: one user, every member of a group, or every user of a tenant.
 */
export enum SubjectType {
    USER = 'USER',
    GROUP = 'GROUP',
    TENANT = 'TENANT'
}
