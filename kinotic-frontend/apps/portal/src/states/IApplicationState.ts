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

    countsLoaded: boolean
    projectsCount: number
    entityDefinitionsCount: number

    currentApplication: Application | null

    loadAllApplications(): Promise<void>
}

class ApplicationState implements IApplicationState {
    public allApplications: Application[] = []

    public countsLoaded = false
    public projectsCount = 0
    public entityDefinitionsCount = 0

    public _currentApplication: Application | null = null

    public set currentApplication(app: Application | null) {
        this._currentApplication = app
        this.countsLoaded = false

        if (app) {
            Promise.all([
                Kinotic.projects.countForApplication(app.id),
                Kinotic.entityDefinitions.countForApplication(app.id)
            ]).then(([projectsCount, entityDefinitionsCount]) => {
                this.projectsCount = projectsCount
                this.entityDefinitionsCount = entityDefinitionsCount
                this.countsLoaded = true
            }).catch(error => {
                debug('Failed to load counts: %O', error)
                this.projectsCount = -1
                this.entityDefinitionsCount = -1
                this.countsLoaded = true
            })
        }
    }

    public get currentApplication(): Application | null {
        return this._currentApplication
    }

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
