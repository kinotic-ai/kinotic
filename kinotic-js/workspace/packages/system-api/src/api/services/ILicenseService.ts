import { SYSTEM_API_ZONE } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { LicenseDetails } from '@/api/model/license/LicenseDetails'

/**
 * The Kinotic Enterprise License key the whole cluster runs on, which system users read and replace without
 * restarting a server. Published in the system zone, which only SYSTEM participants and the system's own cluster
 * members may address.
 */
export interface ILicenseService {

    /** Resolves null when no valid key is installed. */
    findCurrent(): Promise<LicenseDetails | null>

    /**
     * Installs licenseKey for the whole cluster in place of the installed key, resolving with the license it holds.
     * Each node runs on it once the call resolves, except a node the cluster could not reach, which reads it within
     * ten minutes. Rejects a key Minds Ignited did not sign, one naming no licensee or expiry, and one whose grace
     * period has ended.
     */
    install(licenseKey: string): Promise<LicenseDetails>
}

export class LicenseService implements ILicenseService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.LicenseService`)
    }

    public findCurrent(): Promise<LicenseDetails | null> {
        return this.serviceProxy.invoke('findCurrent', [])
    }

    public install(licenseKey: string): Promise<LicenseDetails> {
        return this.serviceProxy.invoke('install', [licenseKey])
    }
}
