package org.kinotic.structures.internal.api.services.impl.insights;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.structures.api.domain.insights.InsightProgress;
import org.kinotic.structures.api.domain.insights.InsightRequest;
import org.kinotic.structures.api.services.insights.DataInsightsService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * Implementation of DataInsightsService that processes user requests through a chain of steps:
 * 1. Request Analysis: Determines if user wants new visualization or to modify existing
 * 2. New Visualization Path: Generates new visualization using AI analysis
 * 3. Modify Existing Path: Loads and modifies existing visualization
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!test")
public class DataInsightsServiceImpl implements DataInsightsService {

  //  private final ChatClient chatClient;
    // private final InsightsContextService contextService;
    // private final EntitiesService entitiesService;
    // private final StructureService structureService;
    // private final ObjectMapper objectMapper;

    // FIXME: Needs spring ai 2 with spring boot 4 support

    @Override
    public Flux<InsightProgress> processRequest(InsightRequest request, Participant participant) {
        log.info("Processing data insights request: '{}'", request.getQuery());
        // FIXME: Needs spring ai 2 with spring boot 4 support
        return Flux.just(InsightProgress.builder()
                                         .type(InsightProgress.ProgressType.ERROR)
                                         .message("Spring AI does not yet support Spring Boot 4")
                                         .timestamp(Instant.now())
                                         .build());
//        return Flux.create(sink -> {
//            try {
//                // Emit start progress
//                sink.next(InsightProgress.builder()
//                                         .type(InsightProgress.ProgressType.STARTED)
//                                         .message("Starting data insights analysis.")
//                                         .timestamp(Instant.now())
//                                         .build());
//
//                // Step 1: Analyze the request to determine intent
//                sink.next(InsightProgress.builder()
//                                         .type(InsightProgress.ProgressType.ANALYZING)
//                                         .message("Analyzing your request.")
//                                         .timestamp(Instant.now())
//                                         .build());
//
//                RequestAnalysisResult analysisResult = analyzeRequest(request);
//                log.info("Request analysis result: {}", analysisResult);
//
//                // Step 2: Route to appropriate processing path
//                if (analysisResult.getRequestType() == RequestAnalysisResult.RequestType.NEW_VISUALIZATION) {
//                    generateNewVisualization(request, participant, sink);
//                } else {
//                    modifyExistingVisualization(request, participant, analysisResult, sink);
//                }
//
//            } catch (Exception e) {
//                log.error("Error processing request: {}", e.getMessage(), e);
//                sink.next(InsightProgress.builder()
//                                         .type(InsightProgress.ProgressType.ERROR)
//                                         .message("An error occurred during analysis")
//                                         .errorMessage(e.getMessage())
//                                         .timestamp(Instant.now())
//                                         .build());
//                sink.complete();
//            }
//        });
    }
//
//    /**
//     * Step 1: Analyzes the user's request to determine if they want a new visualization
//     * or to modify an existing one.
//     */
//    private RequestAnalysisResult analyzeRequest(InsightRequest request) throws Exception {
//        log.debug("Analyzing request intent for query: '{}'", request.getQuery());
//
//        String systemPrompt = contextService.getRequestAnalysisPrompt();
//        String userPrompt = String.format("User request: %s", request.getQuery());
//
//        String aiResponse = chatClient.prompt()
//                                      .system(systemPrompt)
//                                      .user(userPrompt)
//                                      .call()
//                                      .content();
//
//        // Parse the JSON response from the AI
//        try {
//            return objectMapper.readValue(aiResponse.trim(), RequestAnalysisResult.class);
//        } catch (Exception e) {
//            log.warn("Failed to parse AI response as JSON, treating as new visualization request: {}", aiResponse);
//            return new RequestAnalysisResult().setRequestType(RequestAnalysisResult.RequestType.NEW_VISUALIZATION)
//                                              .setAdditionalContext(request.getQuery());
//        }
//    }
//
//    /**
//     * Step 2a: Generates a new visualization using the existing AI pipeline with progress updates.
//     */
//    private void generateNewVisualization(InsightRequest request, Participant participant,
//                                          FluxSink<InsightProgress> sink) {
//        log.info("Generating new visualization for query: '{}'", request.getQuery());
//
//        try {
//            // Create tools with participant context and progress reporting
//            StructureDiscoveryTools progressStructureTools = new StructureDiscoveryTools(structureService, sink);
//            DataAnalysisTools progressDataTools = new DataAnalysisTools(entitiesService, participant, sink);
//
//            // Build context
//            String context = contextService.buildAnalysisContext(request, participant);
//            String systemPrompt = contextService.getHtmlGenerationSystemPrompt();
//            String userPrompt = buildUserPrompt(request, context);
//
//            // Emit code generation progress
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.GENERATING_CODE)
//                                     .message("Generating web components with AI")
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            // Generate visualization with AI (non-streaming)
//            String aiResponse = chatClient
//                    .prompt()
//                    .system(systemPrompt)
//                    .user(userPrompt)
//                    .tools(progressStructureTools, progressDataTools)
//                    .call()
//                    .content();
//
//            // Emit parsing progress
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.GENERATING_CODE)
//                                     .message("Parsing generated components")
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            // Parse components from AI response with retry logic
//            List<DataInsightsComponent> components = parseComponentsFromResponseWithRetry(aiResponse, request, systemPrompt, userPrompt, progressStructureTools, progressDataTools);
//
//            // Emit all components at once since they're all ready
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.COMPONENTS_READY)
//                                     .message("Generated " + components.size() + " visualization(s)")
//                                     .components(components)
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            // Emit completion
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.COMPLETED)
//                                     .message("Analysis completed successfully")
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            sink.complete();
//
//        } catch (Exception e) {
//            log.error("Error generating new visualization: {}", e.getMessage(), e);
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.ERROR)
//                                     .message("Error during analysis")
//                                     .errorMessage(e.getMessage())
//                                     .timestamp(Instant.now())
//                                     .build());
//            sink.complete();
//        }
//    }
//
//    /**
//     * Step 2b: Modifies an existing visualization based on user feedback.
//     */
//    private void modifyExistingVisualization(InsightRequest request, Participant participant,
//                                             RequestAnalysisResult analysisResult,
//                                             FluxSink<InsightProgress> sink) {
//        log.info("Modifying existing visualization: '{}' with request: '{}'",
//                 analysisResult.getVisualizationName(), request.getQuery());
//
//        try {
//            // Emit modification progress
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.GENERATING_CODE)
//                                     .message("Modifying existing visualization.")
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            // TODO: Load the existing visualization code
//            // For now, this is a no-op placeholder
//            String existingVisualizationCode = loadExistingVisualization(analysisResult.getVisualizationName());
//
//            if (existingVisualizationCode == null) {
//                log.warn("Could not load existing visualization '{}', falling back to new visualization",
//                         analysisResult.getVisualizationName());
//                generateNewVisualization(request, participant, sink);
//                return;
//            }
//
//            // Create tools with participant context and progress reporting
//            StructureDiscoveryTools progressStructureTools = new StructureDiscoveryTools(structureService, sink);
//            DataAnalysisTools progressDataTools = new DataAnalysisTools(entitiesService, participant, sink);
//
//            // Build context
//            String context = contextService.buildAnalysisContext(request, participant);
//            String systemPrompt = contextService.getVisualizationModificationPrompt();
//            String userPrompt = buildModificationPrompt(request, context, existingVisualizationCode, analysisResult);
//
//            // Generate modified visualization with AI
//            String aiResponse = chatClient
//                    .prompt()
//                    .system(systemPrompt)
//                    .user(userPrompt)
//                    .tools(progressStructureTools, progressDataTools)
//                    .call()
//                    .content();
//
//            // Parse components and emit progress
//            List<DataInsightsComponent> components = parseComponentsFromResponse(aiResponse, request);
//
//            // Emit all components at once since they're all ready
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.COMPONENTS_READY)
//                                     .message("Modified " + components.size() + " visualization(s)")
//                                     .components(components)
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            // Emit completion
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.COMPLETED)
//                                     .message("Modification completed successfully")
//                                     .timestamp(Instant.now())
//                                     .build());
//
//            sink.complete();
//
//        } catch (Exception e) {
//            log.error("Error modifying existing visualization: {}", e.getMessage(), e);
//            sink.next(InsightProgress.builder()
//                                     .type(InsightProgress.ProgressType.ERROR)
//                                     .message("Error during modification")
//                                     .errorMessage(e.getMessage())
//                                     .timestamp(Instant.now())
//                                     .build());
//            sink.complete();
//        }
//    }
//
//    /**
//     * Loads an existing visualization code (placeholder implementation).
//     */
//    private String loadExistingVisualization(String visualizationName) {
//        // TODO: Implement actual visualization storage and retrieval
//        // For now, return null to indicate no existing visualization found
//        log.debug("Loading existing visualization: {} (placeholder implementation)", visualizationName);
//        return null;
//    }
//
//    /**
//     * Builds the user prompt for new visualization generation.
//     */
//    private String buildUserPrompt(InsightRequest request, String context) {
//        StringBuilder prompt = new StringBuilder();
//        prompt.append("User Query: ").append(request.getQuery()).append("\n\n");
//        prompt.append("Application Context:\n").append(context).append("\n\n");
//
//        // Add hardcoded values for the web components
//        prompt.append("HARDCODED VALUES FOR WEB COMPONENTS:\n");
//        prompt.append("Application ID: ").append(request.getApplicationId()).append("\n");
//
//        if (request.getFocusStructureId() != null) {
//            prompt.append("Structure ID: ").append(request.getFocusStructureId()).append("\n");
//            // Extract structure name from the structure ID
//            String structureName = request.getFocusStructureId().contains(".") ?
//                    request.getFocusStructureId().substring(request.getFocusStructureId().indexOf(".") + 1) :
//                    request.getFocusStructureId();
//            prompt.append("Structure Name: ").append(structureName).append("\n");
//        }
//
//        if (request.getPreferredVisualization() != null) {
//            prompt.append("Preferred Visualization: ").append(request.getPreferredVisualization()).append("\n");
//        }
//
//        if (request.getAdditionalContext() != null) {
//            prompt.append("Additional Context: ").append(request.getAdditionalContext()).append("\n");
//        }
//
//        prompt.append("\nIMPORTANT: In your JavaScript code, hardcode these values directly:");
//        prompt.append("\n- Use '").append(request.getApplicationId()).append("' as the applicationId");
//        if (request.getFocusStructureId() != null) {
//            String structureName = request.getFocusStructureId().contains(".") ?
//                    request.getFocusStructureId().substring(request.getFocusStructureId().indexOf(".") + 1) :
//                    request.getFocusStructureId();
//            prompt.append("\n- Use '").append(structureName).append("' as the structureName");
//            prompt.append("\n- Use '").append(request.getFocusStructureId()).append("' as the structureId");
//        }
//        prompt.append("\n\nPlease analyze this request and generate multiple web components that visualize the data. Return your response as a JSON array of DataInsightsComponent objects as specified in the system prompt.");
//
//        return prompt.toString();
//    }
//
//    /**
//     * Builds the user prompt for visualization modification.
//     */
//    private String buildModificationPrompt(InsightRequest request, String context, String existingCode, RequestAnalysisResult analysisResult) {
//        return "User Modification Request: " + request.getQuery() + "\n\n" +
//                "Application Context:\n" + context + "\n\n" +
//                "Visualization to Modify: " + analysisResult.getVisualizationName() + "\n" +
//                "Additional Context: " + analysisResult.getAdditionalContext() + "\n\n" +
//                "Existing Visualization Code:\n" + existingCode + "\n\n" +
//                "Please modify the existing visualization according to the user's request. Return your response as a JSON array of DataInsightsComponent objects as specified in the system prompt.";
//    }
//
//
//
//    /**
//     * Parses the AI response to extract multiple DataInsightsComponent objects from JSON with retry logic.
//     */
//    private List<DataInsightsComponent> parseComponentsFromResponseWithRetry(String aiResponse, InsightRequest request,
//                                                                             String systemPrompt, String userPrompt,
//                                                                             StructureDiscoveryTools structureTools,
//                                                                             DataAnalysisTools dataTools) {
//        try {
//            return parseComponentsFromResponse(aiResponse, request);
//        } catch (Exception e) {
//            log.warn("Failed to parse AI response, retrying with explicit instruction: {}", e.getMessage());
//
//            // Retry with explicit JSON instruction
//            String retryPrompt = userPrompt + "\n\nCRITICAL: You must respond with ONLY a JSON array of DataInsightsComponent objects. Do not include any other text, explanations, or markdown formatting.";
//
//            String retryResponse = chatClient
//                    .prompt()
//                    .system(systemPrompt)
//                    .user(retryPrompt)
//                    .tools(structureTools, dataTools)
//                    .call()
//                    .content();
//
//            return parseComponentsFromResponse(retryResponse, request);
//        }
//    }
//
//    /**
//     * Parses the AI response to extract multiple DataInsightsComponent objects from JSON.
//     */
//    private List<DataInsightsComponent> parseComponentsFromResponse(String aiResponse, InsightRequest request) {
//        try {
//            // Validate AI response is not empty
//            if (aiResponse == null || aiResponse.trim().isEmpty()) {
//                throw new RuntimeException("AI response was empty");
//            }
//
//            // Check if response is a tool call result (XML format)
//            if (aiResponse.trim().startsWith("<xai:function_call_result")) {
//                throw new RuntimeException("AI returned tool call result instead of JSON components. Retrying with explicit instruction.");
//            }
//
//            // Clean the response and try to parse as JSON array
//            String cleanedResponse = aiResponse.trim();
//
//            // Handle potential markdown code blocks
//            if (cleanedResponse.startsWith("```json")) {
//                cleanedResponse = cleanedResponse.substring(7);
//            }
//            if (cleanedResponse.startsWith("```")) {
//                cleanedResponse = cleanedResponse.substring(3);
//            }
//            if (cleanedResponse.endsWith("```")) {
//                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
//            }
//
//            cleanedResponse = cleanedResponse.trim();
//
//            if (cleanedResponse.isEmpty()) {
//                throw new RuntimeException("AI response was empty after cleaning");
//            }
//
//            // Parse JSON array directly into DataInsightsComponent objects
//            CollectionType collectionType = objectMapper.getTypeFactory()
//                                                        .constructCollectionType(List.class,
//                                                                                 DataInsightsComponent.class);
//            List<DataInsightsComponent> components = objectMapper.readValue(cleanedResponse, collectionType);
//
//            // Validate and set system-generated fields for each component
//            for (DataInsightsComponent component : components) {
//                if (component.getId() == null) {
//                    component.setId(UUID.randomUUID().toString());
//                }
//                if (component.getName() == null || component.getName().trim().isEmpty()) {
//                    component.setName("Generated Visualization");
//                }
//                if (component.getDescription() == null || component.getDescription().trim().isEmpty()) {
//                    component.setDescription("Data visualization component");
//                }
//                if (component.getRawHtml() == null || component.getRawHtml().trim().isEmpty()) {
//                    throw new RuntimeException("Component rawHtml cannot be null or empty");
//                }
//                component.setApplicationId(request.getApplicationId());
//                component.setModifiedAt(Instant.now());
//            }
//
//            if (components.isEmpty()) {
//                throw new RuntimeException("No components were generated");
//            }
//
//            return components;
//
//        } catch (Exception e) {
//            log.error("Failed to parse JSON response from AI: {}", e.getMessage());
//            log.debug("Raw AI response: {}", aiResponse);
//            throw new RuntimeException("AI response was not in the expected JSON format. Expected an array of DataInsightsComponent objects.", e);
//        }
//    }

}