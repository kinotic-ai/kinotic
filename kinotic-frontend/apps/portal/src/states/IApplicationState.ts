import { Kinotic, Pageable } from '@kinotic-ai/core'
import { reactive, type Reactive } from 'vue'
import { createDebug } from '@kinotic-ai/frontend-common'
import { Application, type BetaLimits } from '@kinotic-ai/management-api'

const debug = createDebug('application-state');

/**
 * The organization's applications, the one the current route is scoped to, and the beta plan
 * limiting them. The header keeps {@link currentApplication} in step with the route's
 * applicationId; pages inside that scope read it rather than fetching the application again.
 */
export interface IApplicationState {
    allApplications: Application[]

    currentApplication: Application | null

    /** The free beta cloud plan the organization is on, or null when the server imposes no limits. */
    betaLimits: BetaLimits | null

    /** Loads the organization's applications and, since its usage counts them, the beta plan. */
    loadAllApplications(): Promise<void>
}

class ApplicationState implements IApplicationState {
    public allApplications: Application[] = []

    public currentApplication: Application | null = null

    public betaLimits: BetaLimits | null = null

    public async loadAllApplications(): Promise<void> {
        await Promise.all([this.loadApplications(), this.loadBetaLimits()])
    }

    private async loadApplications(): Promise<void> {
        try {
            const service = Kinotic.applications
            const pageable = Pageable.create(0, 1000)
            const result = await service.findAll(pageable)
            this.allApplications = result.content ?? []
        } catch (error) {
            debug('Failed to load all applications: %O', error)
            this.allApplications = []
        }
    }

    private async loadBetaLimits(): Promise<void> {
        try {
            this.betaLimits = await Kinotic.applications.findBetaLimits()
        } catch (error) {
            debug('Failed to load the beta limits: %O', error)
            this.betaLimits = null
        }
    }
}

export const APPLICATION_STATE: Reactive<IApplicationState> = reactive<IApplicationState>(new ApplicationState())
