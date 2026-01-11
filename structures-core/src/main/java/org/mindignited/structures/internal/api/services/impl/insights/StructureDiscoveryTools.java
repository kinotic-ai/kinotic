package org.mindignited.structures.internal.api.services.impl.insights;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.continuum.idl.api.schema.ArrayC3Type;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.continuum.idl.api.schema.PropertyDefinition;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.insights.InsightProgress;
import org.mindignited.structures.api.services.StructureService;
import org.springframework.ai.tool.annotation.Tool;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

/**
 * Spring AI Tools for discovering and analyzing structure schemas.
 * These tools allow the AI to understand the available data models
 * and their C3 type definitions.
 */
@Slf4j
public class StructureDiscoveryTools {

    private final StructureService structureService;
    private final FluxSink<InsightProgress> progressSink;

    public StructureDiscoveryTools(StructureService structureService, FluxSink<InsightProgress> progressSink) {
        this.structureService = structureService;
        this.progressSink = progressSink;
    }

    public StructureService getStructureService() {
        return structureService;
    }

    /**
     * Tool that allows Spring AI to discover all published structures available in an application.
     * This helps the AI understand what data models the user has access to for analysis.
     * Only published structures have data that can be queried.
     */
    @Tool
    public String getApplicationStructures(String applicationId) {
        log.debug("AI requesting structures for application: {}", applicationId);
        
        if (progressSink != null) {
            progressSink.next(InsightProgress.builder()
                .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                .message("Discovering data structures")
                .timestamp(Instant.now())
                .build());
        }
        
        try {
            // Get all published structures for the application
            Pageable pageable = Pageable.ofSize(100); // Get up to 100 structures
            CompletableFuture<Page<Structure>> structuresFuture = structureService.findAllPublishedForApplication(applicationId, pageable);
            Page<Structure> structures = structuresFuture.join();
            
            if (structures.getContent().isEmpty()) {
                return String.format("No published structures found for application: %s", applicationId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d published structures in application '%s':\n\n", 
                structures.getTotalElements(), applicationId));
            
            for (Structure structure : structures.getContent()) {
                result.append(String.format("Structure: %s\n", structure.getName()));
                result.append(String.format("  ID: %s\n", structure.getId()));
                result.append(String.format("  Description: %s\n", structure.getDescription() != null ? structure.getDescription() : "No description"));
                result.append("\n");
            }
            
            if (progressSink != null) {
                progressSink.next(InsightProgress.builder()
                    .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                    .message("Data structures discovered")
                    .timestamp(Instant.now())
                    .build());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error getting structures for application {}: {}", applicationId, e.getMessage());
            return String.format("Error retrieving structures for application %s: %s", applicationId, e.getMessage());
        }
    }

    /**
     * Tool that allows Spring AI to get detailed schema information for a specific structure.
     * This includes all C3 type definitions, field types, and constraints.
     */
    @Tool  
    public String getStructureSchema(String structureId) {
        log.debug("AI requesting schema for structure: {}", structureId);
        
        if (progressSink != null) {
            progressSink.next(InsightProgress.builder()
                .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                .message("Analyzing structure schema: " + structureId)
                .timestamp(Instant.now())
                .build());
        }
        
        try {
            CompletableFuture<Structure> structureFuture = structureService.findById(structureId);
            Structure structure = structureFuture.join();
            if (structure == null) {
                return String.format("Structure not found: %s", structureId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Schema for Structure: %s\n", structure.getName()));
            result.append(String.format("Application: %s\n", structure.getApplicationId()));
            result.append(String.format("Description: %s\n\n", structure.getDescription() != null ? structure.getDescription() : "No description"));
            
            // Analyze the entity definition (ObjectC3Type)
            ObjectC3Type entityDef = structure.getEntityDefinition();
            if (entityDef != null) {
                result.append("Fields and C3 Types:\n");
                analyzeC3TypeProperties(entityDef, result, 0);
            } else {
                result.append("No entity definition found.\n");
            }
            
            if (progressSink != null) {
                progressSink.next(InsightProgress.builder()
                    .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                    .message("Schema analyzed: " + structureId)
                    .timestamp(Instant.now())
                    .build());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error getting schema for structure {}: {}", structureId, e.getMessage());
            return String.format("Error retrieving schema for structure %s: %s", structureId, e.getMessage());
        }
    }

    /**
     * Tool that allows Spring AI to find published structures by name or description matching a search term.
     * This helps when users reference structures using natural language.
     * Only published structures have data that can be analyzed.
     */
    @Tool
    public String findStructuresByName(String applicationId, String searchTerm) {
        log.debug("AI searching for structures matching '{}' in application: {}", searchTerm, applicationId);
        
        try {
            // Get all published structures and filter by name/description
            Pageable pageable = Pageable.ofSize(100);
            CompletableFuture<Page<Structure>> structuresFuture = structureService.findAllPublishedForApplication(applicationId, pageable);
            Page<Structure> allStructures = structuresFuture.join();
            
            List<Structure> matchingStructures = allStructures.getContent().stream()
                .filter(structure -> 
                    structure.getName().toLowerCase().contains(searchTerm.toLowerCase()) ||
                    (structure.getDescription() != null && 
                     structure.getDescription().toLowerCase().contains(searchTerm.toLowerCase()))
                )
                .toList();
            
            if (matchingStructures.isEmpty()) {
                return String.format("No published structures found matching '%s' in application '%s'", searchTerm, applicationId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d published structures matching '%s':\n\n", matchingStructures.size(), searchTerm));
            
            for (Structure structure : matchingStructures) {
                result.append(String.format("Structure: %s (ID: %s)\n", structure.getName(), structure.getId()));
                result.append(String.format("  Description: %s\n", structure.getDescription() != null ? structure.getDescription() : "No description"));
                result.append(String.format("  Match reason: Name or description contains '%s'\n\n", searchTerm));
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error searching structures for term '{}' in application {}: {}", searchTerm, applicationId, e.getMessage());
            return String.format("Error searching structures: %s", e.getMessage());
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
                else if (fieldType instanceof ArrayC3Type) {
                    ArrayC3Type arrayType = (ArrayC3Type) fieldType;
                    C3Type elementType = arrayType.getContains();
                    if (elementType instanceof ObjectC3Type) {
                        result.append(String.format("%s  Array elements have structure:\n", indent));
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
                return "Object (nested structure)";
            default:
                return typeName.replace("C3Type", "");
        }
    }


}