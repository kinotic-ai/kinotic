import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'
import type { ApplicationServiceContract, AuthorizationIdentityOption, AuthorizationPolicy, AuthorizationPolicyView, AuthorizationScope } from '../../model/security/AuthorizationPolicy'

export interface IAccessControlService {
    load(scope: AuthorizationScope): Promise<AuthorizationPolicyView>
    save(policy: AuthorizationPolicy, expectedRevision: number): Promise<AuthorizationPolicyView>
    initialize(scope: AuthorizationScope, administratorIdentityId: string): Promise<AuthorizationPolicyView>
    republish(scope: AuthorizationScope, expectedRevision: number): Promise<AuthorizationPolicyView>
    publishContracts(applicationId: string, contracts: ApplicationServiceContract[]): Promise<void>
    findIdentities(scope: AuthorizationScope, pageable: Pageable): Promise<Page<AuthorizationIdentityOption>>
}
export class AccessControlService implements IAccessControlService {
    private readonly service: IServiceProxy
    constructor(kinotic: IKinotic) { this.service = kinotic.serviceProxy('app-api~org.kinotic.domain.api.services.security.authorization.AccessControlService') }
    load(scope: AuthorizationScope): Promise<AuthorizationPolicyView> { return this.service.invoke('load', [scope]) }
    save(policy: AuthorizationPolicy, expectedRevision: number): Promise<AuthorizationPolicyView> { return this.service.invoke('save', [policy, expectedRevision]) }
    initialize(scope: AuthorizationScope, administratorIdentityId: string): Promise<AuthorizationPolicyView> { return this.service.invoke('initialize', [scope, administratorIdentityId]) }
    republish(scope: AuthorizationScope, expectedRevision: number): Promise<AuthorizationPolicyView> { return this.service.invoke('republish', [scope, expectedRevision]) }
    publishContracts(applicationId: string, contracts: ApplicationServiceContract[]): Promise<void> { return this.service.invoke('publishContracts', [applicationId, contracts]) }
    findIdentities(scope: AuthorizationScope, pageable: Pageable): Promise<Page<AuthorizationIdentityOption>> { return this.service.invoke('findIdentities', [scope, pageable]) }
}
