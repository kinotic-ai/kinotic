import type {DataInsightsComponent} from './DataInsightsComponent.js'

export class DataInsightsWidget {
    public id: string | null = null
    public applicationId!: string
    public dataInsightsComponent!: DataInsightsComponent
    public dashboardId?: string // Optional reference to dashboard
    public created!: Date
    public updated!: Date
}
