import { Kinotic, Pageable } from '@kinotic-ai/core'
import { reactive, type Reactive } from 'vue'
import { createDebug } from '@kinotic-ai/frontend-common'
import { Application } from '@kinotic-ai/management-api'

const debug = createDebug('application-state');

/**
 * The organization's applications and the one the current route is scoped to. The header
 * keeps {@link currentApplication} in step with the route's applicationId; pages inside
 * that scope read it rather than fetching the application again.
 */
export interface IApplicationState {
    allApplications: Application[]

    currentApplication: Application | null

    loadAllApplications(): Promise<void>
}

class ApplicationState implements IApplicationState {
    public allApplications: Application[] = []

    public currentApplication: Application | null = null

    public async loadAllApplications(): Promise<void> {
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
}

export const APPLICATION_STATE: Reactive<IApplicationState> = reactive<IApplicationState>(new ApplicationState())
