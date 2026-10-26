/** A person to add to one of the caller's organization's applications, as `MemberService.addApplicationUser` takes them. */
export class AddApplicationUserRequest {

    /** The address the person signs in with. */
    public email: string = ''

    /** The person's display name, or null. */
    public displayName: string | null = null

    /** The application the person is added to, one of the caller's organization's. */
    public applicationId: string = ''

    /**
     * The application's tenant the person joins, required unless the system manages the application's tenants, which
     * gives the person a tenant of their own, when it is always null.
     */
    public tenantId: string | null = null

    /** Whether to email the person an invitation. */
    public sendInvitation: boolean = true
}
