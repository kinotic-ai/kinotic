package org.kinotic.persistence.internal.api.services.insights;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.insights.InsightProgress;
import org.kinotic.persistence.api.services.EntityDefinitionService;
import reactor.core.publisher.FluxSink;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Spring AI Tools for discovering and analyzing EntityDefinition schemas.
 * These tools allow the AI to understand the available data models
 * and their C3 type definitions.
 */
@Slf4j
public class EntityDefinitionDiscoveryTools {

    private final EntityDefinitionService entityDefinitionService;
    private final FluxSink<InsightProgress> progressSink;

    public EntityDefinitionDiscoveryTools(EntityDefinitionService entityDefinitionService, FluxSink<InsightProgress> progressSink) {
        this.entityDefinitionService = entityDefinitionService;
        this.progressSink = progressSink;
    }


    // FIXME: Needs spring ai 2 with spring boot 4 support

    /**
     * Tool that allows Spring AI to discover all published EntityDefinitions available in an application.
     * This helps the AI understand what data models the user has access to for analysis.
     * Only published EntityDefinitions have data that can be queried.
     */
   // @Tool
    public String getApplicationEntityDefinitions(String applicationId) {
        log.debug("AI requesting EntityDefinitions for application: {}", applicationId);
        
        if (progressSink != null) {
            progressSink.next(InsightProgress.builder()
                .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                .message("Discovering data EntityDefinitions")
                .timestamp(Instant.now())
                .build());
        }
        
        try {
            // Get all published EntityDefinitions for the application
            Pageable pageable = Pageable.ofSize(100); // Get up to 100 EntityDefinitions
            CompletableFuture<Page<EntityDefinition>> entityDefinitionFuture = entityDefinitionService.findAllPublishedForApplication(applicationId, pageable);
            Page<EntityDefinition> entityDefinitionPage = entityDefinitionFuture.join();
            
            if (entityDefinitionPage.getContent().isEmpty()) {
                return String.format("No published EntityDefinitions found for application: %s", applicationId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d published EntityDefinitions in application '%s':\n\n",
                entityDefinitionPage.getTotalElements(), applicationId));
            
            for (EntityDefinition entityDefinition : entityDefinitionPage.getContent()) {
                result.append(String.format("EntityDefinition: %s\n", entityDefinition.getName()));
                result.append(String.format("  ID: %s\n", entityDefinition.getId()));
                result.append(String.format("  Description: %s\n", entityDefinition.getDescription() != null ? entityDefinition.getDescription() : "No description"));
                result.append("\n");
            }
            
            if (progressSink != null) {
                progressSink.next(InsightProgress.builder()
                    .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                    .message("Data EntityDefinitions discovered")
                    .timestamp(Instant.now())
                    .build());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error getting EntityDefinitions for application {}: {}", applicationId, e.getMessage());
            return String.format("Error retrieving EntityDefinitions for application %s: %s", applicationId, e.getMessage());
        }
    }

    /**
     * Tool that allows Spring AI to get detailed schema information for a specific EntityDefinition.
     * This includes all C3 type definitions, field types, and constraints.
     */
   // @Tool
    public String getEntityDefinitionSchema(String entityDefinitionId) {
        log.debug("AI requesting schema for EntityDefinition: {}", entityDefinitionId);
        
        if (progressSink != null) {
            progressSink.next(InsightProgress.builder()
                .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                .message("Analyzing EntityDefinition schema: " + entityDefinitionId)
                .timestamp(Instant.now())
                .build());
        }
        
        try {
            CompletableFuture<EntityDefinition> future = entityDefinitionService.findById(entityDefinitionId);
            EntityDefinition entityDefinition = future.join();
            if (entityDefinition == null) {
                return String.format("EntityDefinition not found: %s", entityDefinitionId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Schema for EntityDefinition: %s\n", entityDefinition.getName()));
            result.append(String.format("Application: %s\n", entityDefinition.getApplicationId()));
            result.append(String.format("Description: %s\n\n", entityDefinition.getDescription() != null ? entityDefinition.getDescription() : "No description"));
            
            // Analyze the entity definition (ObjectC3Type)
            ObjectC3Type entityDef = entityDefinition.getSchema();
            if (entityDef != null) {
                result.append("Fields and C3 Types:\n");
                analyzeC3TypeProperties(entityDef, result, 0);
            } else {
                result.append("No entity definition found.\n");
            }
            
            if (progressSink != null) {
                progressSink.next(InsightProgress.builder()
                    .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                    .message("Schema analyzed: " + entityDefinitionId)
                    .timestamp(Instant.now())
                    .build());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error getting schema for EntityDefinition {}: {}", entityDefinitionId, e.getMessage());
            return String.format("Error retrieving schema for EntityDefinition %s: %s", entityDefinitionId, e.getMessage());
        }
    }

    /**
     * Tool that allows Spring AI to find published EntityDefinitions by name or description matching a search term.
     * This helps when users reference EntityDefinitions using natural language.
     * Only published EntityDefinitions have data that can be analyzed.
     */
   // @Tool
    public String findEntityDefinitionsByName(String applicationId, String searchTerm) {
        log.debug("AI searching for EntityDefinitions matching '{}' in application: {}", searchTerm, applicationId);
        
        try {
            // Get all published EntityDefinitions and filter by name/description
            Pageable pageable = Pageable.ofSize(100);
            CompletableFuture<Page<EntityDefinition>> future = entityDefinitionService.findAllPublishedForApplication(applicationId, pageable);
            Page<EntityDefinition> entityDefinitionPage = future.join();
            
            List<EntityDefinition> matchingEntityDefinitions = entityDefinitionPage.getContent().stream()
                                                                            .filter(entityDefinition ->
                    entityDefinition.getName().toLowerCase().contains(searchTerm.toLowerCase()) ||
                    (entityDefinition.getDescription() != null &&
                     entityDefinition.getDescription().toLowerCase().contains(searchTerm.toLowerCase()))
                )
                                                                            .toList();
            
            if (matchingEntityDefinitions.isEmpty()) {
                return String.format("No published EntityDefinitions found matching '%s' in application '%s'", searchTerm, applicationId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d published EntityDefinitions matching '%s':\n\n", matchingEntityDefinitions.size(), searchTerm));
            
            for (EntityDefinition entityDefinition : matchingEntityDefinitions) {
                result.append(String.format("EntityDefinition: %s (ID: %s)\n", entityDefinition.getName(), entityDefinition.getId()));
                result.append(String.format("  Description: %s\n", entityDefinition.getDescription() != null ? entityDefinition.getDescription() : "No description"));
                result.append(String.format("  Match reason: Name or description contains '%s'\n\n", searchTerm));
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error searching EntityDefinitions for term '{}' in application {}: {}", searchTerm, applicationId, e.getMessage());
            return String.format("Error searching EntityDefinitions: %s", e.getMessage());
        }
    }

    /**
     * Recursively analyzes C3 type properties and builds a readable description.
     */
    private void analyzeC3TypeProperties(ObjectC3Type objectType, StringBuilder result, int depth) {
        String indent = "  ".repeat(depth);
        
        if (objectType.getProperties() != null) {
            for (PropertyDefinition property : objectType.getProperties()) {
                String fieldName = property.getName();
                C3Type fieldType = property.getType();
                
                result.append(String.format("%s- %s: %s\n", indent, fieldName, getC3TypeDescription(fieldType)));
                
                // If this is an ObjectC3Type, recursively analyze its properties
                if (fieldType instanceof ObjectC3Type) {
                    analyzeC3TypeProperties((ObjectC3Type) fieldType, result, depth + 1);
                }
                // If this is an ArrayC3Type, analyze the element type
                else if (fieldType instanceof ArrayC3Type arrayType) {
                    C3Type elementType = arrayType.getContains();
                    if (elementType instanceof ObjectC3Type) {
                        result.append(String.format("%s  Array elements have EntityDefinition:\n", indent));
                        analyzeC3TypeProperties((ObjectC3Type) elementType, result, depth + 2);
                    }
                }
            }
        }
    }

    /**
     * Gets a human-readable description of a C3Type.
     */
    private String getC3TypeDescription(C3Type type) {
        if (type == null) {
            return "Unknown type";
        }
        
        String typeName = type.getClass().getSimpleName();
        
        // Add additional details for specific types
        switch (typeName) {
            case "StringC3Type":
                return "String (text)";
            case "IntC3Type":
                return "Integer (whole number)";
            case "LongC3Type":
                return "Long (large whole number)";
            case "DoubleC3Type":
                return "Double (decimal number)";
            case "BooleanC3Type":
                return "Boolean (true/false)";
            case "DateC3Type":
                return "Date (timestamp)";
            case "ArrayC3Type":
                ArrayC3Type arrayType = (ArrayC3Type) type;
                C3Type elementType = arrayType.getContains();
                String elementDescription = getC3TypeDescription(elementType);
                return String.format("Array of %s", elementDescription);
            case "EnumC3Type":
                return "Enum (predefined values)";
            case "ObjectC3Type":
                return "Object (nested EntityDefinition)";
            default:
                return typeName.replace("C3Type", "");
        }
    }


}