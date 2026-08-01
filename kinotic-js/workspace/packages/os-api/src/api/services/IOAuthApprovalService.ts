import { OS_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'

/**
 * What the consent page shows about an OAuth authorization request awaiting approval.
 */
export interface PendingOAuthAuthorization {
    clientName: string
    scope: string | null
}

/**
 * Browser-invoked service for the OAuth 2.1 consent step. The signed-in user reviews the
 * pending authorization request and approves or denies it; either decision returns the
 * client's redirect URL the browser must navigate to.
 */
export interface IOAuthApprovalService {

    /**
     * Describes the authorization request awaiting consent. Rejects when the request is
     * unknown, expired, or already decided.
     */
    describe(requestId: string): Promise<PendingOAuthAuthorization>

    /**
     * Approves the authorization request as the calling user and returns the redirect URL
     * carrying the authorization code.
     */
    approve(requestId: string): Promise<string>

    /**
     * Denies the authorization request and returns the redirect URL carrying
     * {@code error=access_denied}.
     */
    deny(requestId: string): Promise<string>

    /**
     * Approves the pending RFC 8628 device grant identified by {@code userCode} as the calling
     * user. Rejects when the code is unknown, already approved, or expired.
     */
    approveDevice(userCode: string): Promise<void>

}

export class OAuthApprovalService implements IOAuthApprovalService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${OS_API_ZONE}~org.kinotic.os.api.services.iam.OAuthApprovalService`)
    }

    public describe(requestId: string): Promise<PendingOAuthAuthorization> {
        return this.serviceProxy.invoke('describe', [requestId])
    }

    public approve(requestId: string): Promise<string> {
        return this.serviceProxy.invoke('approve', [requestId])
    }

    public deny(requestId: string): Promise<string> {
        return this.serviceProxy.invoke('deny', [requestId])
    }

    public approveDevice(userCode: string): Promise<void> {
        return this.serviceProxy.invoke('approveDevice', [userCode])
    }
}
