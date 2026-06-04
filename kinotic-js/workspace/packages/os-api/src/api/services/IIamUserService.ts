import { CrudServiceProxy, type IKinotic, type ICrudServiceProxy } from '@kinotic-ai/core'
import { IamUser } from '@/api/model/iam/IamUser'

/**
 * Client proxy for the server's {@code IamUserService}; method shapes mirror the Java interface.
 *
 * NOTE: the server interface now lives in {@code kinotic-domain} and is not currently
 * {@code @Publish}-annotated, so this proxy is not callable over RPC until the backend publishes
 * the service. The shape here is kept in sync so it is ready once that happens; confirm the
 * published namespace before relying on it.
 */
export interface IIamUserService extends ICrudServiceProxy<IamUser> {

    /** Finds the first user with the given email across all scopes, or null. */
    findByEmail(email: string): Promise<IamUser | null>

    /**
     * Finds the user with the given email within a scope identified structurally by
     * (organizationId, applicationId): both null → SYSTEM, organizationId only → ORGANIZATION,
     * both set → APPLICATION.
     */
    findByEmail(email: string, organizationId: string | null, applicationId: string | null): Promise<IamUser | null>

    /**
     * Finds the first ORGANIZATION-scope user with the given email across all organizations.
     * Used by sign-up to enforce one user per email before the new org's id exists.
     */
    findFirstOrgUserByEmail(email: string): Promise<IamUser | null>

    /**
     * Finds the user (if any) with the given OIDC identity within a specific scope, using the
     * same (organizationId, applicationId) null conventions as {@link findByEmail}.
     */
    findByOidcIdentity(oidcSubject: string,
                       oidcConfigId: string,
                       organizationId: string | null,
                       applicationId: string | null): Promise<IamUser | null>

    /** Finds every user across scopes for a given OIDC identity — e.g. the orgs it can access. */
    findAllByOidcIdentity(oidcSubject: string, oidcConfigId: string): Promise<IamUser[]>

    /** Creates a user and, if a password is provided, the matching credential. */
    createUser(user: IamUser, password: string | null): Promise<IamUser>

}

export class IamUserService extends CrudServiceProxy<IamUser> implements IIamUserService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.domain.api.services.iam.IamUserService'))
    }

    public findByEmail(email: string, organizationId?: string | null, applicationId?: string | null): Promise<IamUser | null> {
        return arguments.length >= 3
            ? this.serviceProxy.invoke('findByEmail', [email, organizationId, applicationId])
            : this.serviceProxy.invoke('findByEmail', [email])
    }

    public findFirstOrgUserByEmail(email: string): Promise<IamUser | null> {
        return this.serviceProxy.invoke('findFirstOrgUserByEmail', [email])
    }

    public findByOidcIdentity(oidcSubject: string,
                              oidcConfigId: string,
                              organizationId: string | null,
                              applicationId: string | null): Promise<IamUser | null> {
        return this.serviceProxy.invoke('findByOidcIdentity', [oidcSubject, oidcConfigId, organizationId, applicationId])
    }

    public findAllByOidcIdentity(oidcSubject: string, oidcConfigId: string): Promise<IamUser[]> {
        return this.serviceProxy.invoke('findAllByOidcIdentity', [oidcSubject, oidcConfigId])
    }

    public createUser(user: IamUser, password: string | null): Promise<IamUser> {
        return this.serviceProxy.invoke('createUser', [user, password])
    }

}
