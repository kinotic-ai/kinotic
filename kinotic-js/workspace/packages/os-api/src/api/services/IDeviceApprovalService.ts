import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'

/**
 * Browser-invoked service for the RFC 8628 device-authorization approve step. The signed-in
 * browser user calls {@link approve} with the user_code shown in their CLI; the gateway
 * binds the pending grant to the calling participant.
 */
export interface IDeviceApprovalService {

    /**
     * Binds the calling user to the pending device-authorization grant identified by
     * {@code userCode}. Rejects when the code is unknown, already approved, or expired.
     */
    approve(userCode: string): Promise<void>

}

export class DeviceApprovalService implements IDeviceApprovalService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('api.org.kinotic.os.api.services.iam.DeviceApprovalService')
    }

    public approve(userCode: string): Promise<void> {
        return this.serviceProxy.invoke('approve', [userCode])
    }
}
