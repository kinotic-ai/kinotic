import { CrudServiceProxy, type IKinotic, type ICrudServiceProxy, type Page, type Pageable } from '@kinotic-ai/core'
import { IamUser } from '@/api/model/iam/IamUser'

export interface IIamUserService extends ICrudServiceProxy<IamUser> {

    /**
     * Finds the user with the given email within the given auth scope.
     * @return Promise emitting the user or null if no user matches
     */
    findByEmailAndScope(email: string, authScopeType: string, authScopeId: string): Promise<IamUser | null>

    /**
     * Finds all users registered against the given auth scope.
     */
    findByScope(authScopeType: string, authScopeId: string, pageable: Pageable): Promise<Page<IamUser>>

    /**
     * Creates a user and, if a password is provided, the matching credential.
     * APPLICATION-scoped users must carry a {@code tenantId}; SYSTEM and ORGANIZATION users must not.
     */
    createUser(user: IamUser, password: string | null): Promise<IamUser>

    /**
     * Verifies the current password and updates it. Used when the user knows their current password.
     */
    changePassword(userId: string, currentPassword: string, newPassword: string): Promise<void>

    /**
     * Replaces the user's password without verifying the current one. Administrative reset.
     */
    resetPassword(userId: string, newPassword: string): Promise<void>

}

export class IamUserService extends CrudServiceProxy<IamUser> implements IIamUserService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.os.api.services.iam.IamUserService'))
    }

    public findByEmailAndScope(email: string, authScopeType: string, authScopeId: string): Promise<IamUser | null> {
        return this.serviceProxy.invoke('findByEmailAndScope', [email, authScopeType, authScopeId])
    }

    public findByScope(authScopeType: string, authScopeId: string, pageable: Pageable): Promise<Page<IamUser>> {
        return this.serviceProxy.invoke('findByScope', [authScopeType, authScopeId, pageable])
    }

    public createUser(user: IamUser, password: string | null): Promise<IamUser> {
        return this.serviceProxy.invoke('createUser', [user, password])
    }

    public changePassword(userId: string, currentPassword: string, newPassword: string): Promise<void> {
        return this.serviceProxy.invoke('changePassword', [userId, currentPassword, newPassword])
    }

    public resetPassword(userId: string, newPassword: string): Promise<void> {
        return this.serviceProxy.invoke('resetPassword', [userId, newPassword])
    }

}
