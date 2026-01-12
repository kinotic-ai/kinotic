import { Continuum, IServiceProxy } from '@mindignited/continuum-client'
import { InsightRequest } from '@/api/domain/insights/InsightRequest'
import { InsightProgress } from '@/api/domain/insights/InsightProgress'
import { Observable } from 'rxjs'

/**
 * Provides AI-powered data analysis and visualization code generation capabilities.
 * This service analyzes user queries about their structures and generates appropriate
 * web components with charts and visualizations.
 */
export interface IDataInsightsService {

    /**
     * Processes a user's natural language request with real-time progress updates.
     * This method returns an Observable that emits progress updates as the analysis progresses.
     *
     * @param request the analysis request containing query and context
     * @return Observable that emits progress updates and completes with the final response
     */
    processRequest(request: InsightRequest): Observable<InsightProgress>;
}

export class DataInsightsService implements IDataInsightsService {

    private readonly serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Continuum.serviceProxy('org.mindignited.structures.api.services.insights.DataInsightsService')
    }

    public processRequest(request: InsightRequest): Observable<InsightProgress> {
        return this.serviceProxy.invokeStream('processRequest', [request])
    }
}
