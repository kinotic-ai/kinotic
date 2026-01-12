package org.mindignited.structures.api.services.insights;

import org.mindignited.continuum.api.annotations.Publish;
import org.mindignited.continuum.api.security.Participant;
import org.mindignited.structures.api.domain.insights.InsightProgress;
import org.mindignited.structures.api.domain.insights.InsightRequest;

import reactor.core.publisher.Flux;

/**
 * Provides AI-powered data analysis and visualization code generation capabilities.
 * This service analyzes user queries about their structures and generates appropriate
 * web components with charts and visualizations.
 * 
 */
@Publish
public interface DataInsightsService {

    /**
     * Processes a user's natural language request with real-time progress updates.
     * This method returns a Flux that emits progress updates as the analysis progresses.
     * 
     * @param request the analysis request containing query and context
     * @param participant the participant of the logged-in user
     * @return Flux that emits progress updates and completes with the final response
     */
    Flux<InsightProgress> processRequest(InsightRequest request, Participant participant);
}