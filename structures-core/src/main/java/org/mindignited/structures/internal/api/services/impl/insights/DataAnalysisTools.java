package org.mindignited.structures.internal.api.services.impl.insights;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.mindignited.continuum.api.security.Participant;
import org.mindignited.continuum.core.api.crud.Page;
import org.mindignited.continuum.core.api.crud.Pageable;
import org.mindignited.structures.internal.api.domain.DefaultEntityContext;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.insights.InsightProgress;
import org.mindignited.structures.api.services.EntitiesService;
import org.springframework.ai.tool.annotation.Tool;
import reactor.core.publisher.FluxSink;

/**
 * Spring AI Tools for analyzing actual data from structures using EntitiesService.
 * These tools allow the AI to understand data patterns, distributions, and content
 * to make informed visualization recommendations.
 */
@Slf4j
@SuppressWarnings("rawtypes")
public class DataAnalysisTools {

    private final EntitiesService entitiesService;
    private final Participant participant;
    private final FluxSink<InsightProgress> progressSink;
    
    public DataAnalysisTools(EntitiesService entitiesService, Participant participant, FluxSink<InsightProgress> progressSink) {
        this.entitiesService = entitiesService;
        this.participant = participant;
        this.progressSink = progressSink;
    }

    /**
     * Tool that allows Spring AI to get sample data from any structure.
     * This helps the AI understand the actual content and patterns in the data.
     * 
     * Note: This is a simplified synchronous wrapper for the async EntitiesService.
     * In production, consider making tools async or using reactive patterns.
     */
    @Tool
    @SuppressWarnings("unchecked")
    public String getSampleData(String structureId, int sampleSize) {
        log.debug("AI requesting {} sample records from structure: {}", sampleSize, structureId);
        
        progressSink.next(InsightProgress.builder()
            .type(InsightProgress.ProgressType.DISCOVERING_DATA)
            .message("Analyzing data from: " + structureId)
            .timestamp(Instant.now())
            .build());
        
        try {
            // Limit sample size to prevent overwhelming responses
            int limitedSampleSize = Math.min(sampleSize, 50);
            
            // Create EntityContext for the operation
            EntityContext context = createEntityContext();
            
            Pageable pageable = Pageable.ofSize(limitedSampleSize);
            CompletableFuture<Page<Map>> future = entitiesService.findAll(structureId, pageable, Map.class, context);
            Page<Map> dataPage = future.join(); // Blocking wait - not ideal but needed for tool interface
            
            if (dataPage.getContent().isEmpty()) {
                return String.format("No data found in structure: %s", structureId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Sample Data from Structure '%s' (%d of %d records):\n\n", 
                structureId, dataPage.getContent().size(), dataPage.getTotalElements()));
            
            List<Map> content = dataPage.getContent();
            for (int i = 0; i < content.size(); i++) {
                Map<String, Object> record = content.get(i);
                result.append(String.format("Record %d:\n", i + 1));
                result.append(formatDataRecord(record));
                result.append("\n");
            }
            
            // Add data summary
            result.append(String.format("\nData Summary:\n"));
            result.append(String.format("- Total records available: %d\n", dataPage.getTotalElements()));
            result.append(String.format("- Sample size: %d\n", content.size()));
            result.append(String.format("- Fields detected: %s\n", detectFields(content)));
            
            progressSink.next(InsightProgress.builder()
                .type(InsightProgress.ProgressType.DISCOVERING_DATA)
                .message("Data analysis completed creating visualizations: " + structureId)
                .timestamp(Instant.now())
                .build());
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error getting sample data for structure {}: {}", structureId, e.getMessage());
            return String.format("Error retrieving sample data for structure %s: %s", structureId, e.getMessage());
        }
    }

    /**
     * Tool that allows Spring AI to get statistical information about data in a structure.
     */
    @Tool
    public String getDataStatistics(String structureId) {
        log.debug("AI requesting data statistics for structure: {}", structureId);
        
        try {
            EntityContext context = createEntityContext();
            
            // Get total count
            CompletableFuture<Long> countFuture = entitiesService.count(structureId, context);
            long totalCount = countFuture.join();
            
            if (totalCount == 0) {
                return String.format("Structure '%s' contains no data.", structureId);
            }
            
            // Get sample for field analysis
            Pageable pageable = Pageable.ofSize(Math.min(100, (int) totalCount));
            CompletableFuture<Page<Map>> dataFuture = entitiesService.findAll(structureId, pageable, Map.class, context);
            Page<Map> sampleData = dataFuture.join();
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Data Statistics for Structure '%s':\n\n", structureId));
            result.append(String.format("Total Records: %d\n", totalCount));
            result.append(String.format("Sample Size for Analysis: %d\n\n", sampleData.getContent().size()));
            
            // Analyze field characteristics
            if (!sampleData.getContent().isEmpty()) {
                Map<String, FieldStats> fieldStats = analyzeFields(sampleData.getContent());
                
                result.append("Field Analysis:\n");
                fieldStats.forEach((fieldName, stats) -> {
                    result.append(String.format("- %s: %s\n", fieldName, stats.toString()));
                });
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error getting statistics for structure {}: {}", structureId, e.getMessage());
            return String.format("Error retrieving statistics for structure %s: %s", structureId, e.getMessage());
        }
    }

    /**
     * Tool that allows Spring AI to search and analyze specific data patterns.
     */
    @Tool
    @SuppressWarnings("unchecked")
    public String searchAndAnalyze(String structureId, String searchQuery, int limit) {
        log.debug("AI searching structure {} with query: '{}'", structureId, searchQuery);
        
        try {
            int limitedSize = Math.min(limit, 50);
            EntityContext context = createEntityContext();
            Pageable pageable = Pageable.ofSize(limitedSize);
            
            CompletableFuture<Page<Map>> searchFuture = entitiesService.search(structureId, searchQuery, pageable, Map.class, context);
            Page<Map> searchResults = searchFuture.join();
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Search Results for '%s' in Structure '%s':\n\n", searchQuery, structureId));
            
            if (searchResults.getContent().isEmpty()) {
                result.append("No matching records found.\n");
                return result.toString();
            }
            
            result.append(String.format("Found %d matching records (showing %d):\n\n", 
                searchResults.getTotalElements(), searchResults.getContent().size()));
            
            List<Map> content = searchResults.getContent();
            for (int i = 0; i < Math.min(10, content.size()); i++) { // Show max 10 examples
                Map<String, Object> record = content.get(i);
                result.append(String.format("Match %d:\n", i + 1));
                result.append(formatDataRecord(record));
                result.append("\n");
            }
            
            // Add pattern analysis
            result.append("Pattern Analysis:\n");
            result.append(analyzeSearchPatterns(content, searchQuery));
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error searching structure {} with query '{}': {}", structureId, searchQuery, e.getMessage());
            return String.format("Error searching structure %s: %s", structureId, e.getMessage());
        }
    }

    /**
     * Tool that analyzes field value distributions to help with visualization choices.
     */
    @Tool
    @SuppressWarnings("unchecked")
    public String analyzeFieldDistribution(String structureId, String fieldName) {
        log.debug("AI analyzing distribution of field '{}' in structure: {}", fieldName, structureId);
        
        try {
            // Get sample data to analyze the field
            EntityContext context = createEntityContext();
            Pageable pageable = Pageable.ofSize(100);
            CompletableFuture<Page<Map>> dataFuture = entitiesService.findAll(structureId, pageable, Map.class, context);
            Page<Map> sampleData = dataFuture.join();
            
            if (sampleData.getContent().isEmpty()) {
                return String.format("No data available to analyze field '%s' in structure '%s'", fieldName, structureId);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("Field Distribution Analysis for '%s' in Structure '%s':\n\n", fieldName, structureId));
            
            Map<Object, Integer> valueDistribution = new HashMap<>();
            int nullValues = 0;
            
            for (Map<String, Object> record : sampleData.getContent()) {
                Object fieldValue = extractFieldValue(record, fieldName);
                if (fieldValue == null) {
                    nullValues++;
                } else {
                    valueDistribution.merge(fieldValue, 1, Integer::sum);
                }
            }
            
            // Calculate total non-null values from the distribution
            final int totalValues = valueDistribution.values().stream().mapToInt(Integer::intValue).sum();
            
            if (totalValues == 0) {
                result.append(String.format("Field '%s' not found or contains no values.\n", fieldName));
                return result.toString();
            }
            
            result.append(String.format("Total values analyzed: %d\n", totalValues + nullValues));
            result.append(String.format("Non-null values: %d\n", totalValues));
            result.append(String.format("Null values: %d\n\n", nullValues));
            
            // Show top value distributions
            result.append("Value Distribution (top 10):\n");
            valueDistribution.entrySet().stream()
                .sorted(Map.Entry.<Object, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    double percentage = (entry.getValue() * 100.0) / totalValues;
                    result.append(String.format("- %s: %d (%.1f%%)\n", entry.getKey(), entry.getValue(), percentage));
                });
            
            // Determine field characteristics
            result.append(String.format("\nField Characteristics:\n"));
            result.append(String.format("- Unique values: %d\n", valueDistribution.size()));
            result.append(String.format("- Data type: %s\n", determineFieldType(valueDistribution.keySet())));
            result.append(String.format("- Suitable for categorical analysis: %s\n", valueDistribution.size() <= 20 ? "Yes" : "No"));
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error analyzing field distribution for '{}' in structure {}: {}", fieldName, structureId, e.getMessage());
            return String.format("Error analyzing field '%s': %s", fieldName, e.getMessage());
        }
    }

    /**
     * Formats a data record for readable display.
     */
    private String formatDataRecord(Map<String, Object> record) {
        try {
            if (record == null) {
                return "  null\n";
            }
            
            StringBuilder formatted = new StringBuilder();
            record.forEach((key, value) -> {
                formatted.append(String.format("  %s: %s\n", key, value));
            });
            
            return formatted.toString();
            
        } catch (Exception e) {
            return String.format("  Error formatting record: %s\n", e.getMessage());
        }
    }

    /**
     * Detects field names from sample data.
     */
    @SuppressWarnings("unchecked")
    private String detectFields(List<Map> content) {
        try {
            if (content.isEmpty()) {
                return "No fields detected";
            }
            
            Map<String, Object> firstRecord = content.get(0);
            return String.join(", ", firstRecord.keySet());
            
        } catch (Exception e) {
            return "Error detecting fields: " + e.getMessage();
        }
    }

    /**
     * Analyzes field characteristics from sample data.
     */
    @SuppressWarnings("unchecked")
    private Map<String, FieldStats> analyzeFields(List<Map> content) {
        Map<String, FieldStats> fieldStats = new HashMap<>();
        
        try {
            for (Map<String, Object> record : content) {
                record.forEach((fieldName, value) -> {
                    fieldStats.computeIfAbsent(fieldName, k -> new FieldStats()).addValue(value);
                });
            }
        } catch (Exception e) {
            log.warn("Error analyzing fields: {}", e.getMessage());
        }
        
        return fieldStats;
    }

    /**
     * Extracts a specific field value from a record.
     */
    private Object extractFieldValue(Map<String, Object> record, String fieldName) {
        try {
            return record.get(fieldName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Analyzes patterns in search results.
     */
    private String analyzeSearchPatterns(List<Map> searchResults, String searchQuery) {
        StringBuilder analysis = new StringBuilder();
        
        try {
            // Basic pattern analysis
            analysis.append(String.format("- Search term '%s' found in %d records\n", searchQuery, searchResults.size()));
            
            // TODO: Add more sophisticated pattern analysis
            // - Which fields contained the search term
            // - Common patterns in matching records
            // - Distribution of matches across different field values
            
        } catch (Exception e) {
            analysis.append("Error analyzing search patterns: ").append(e.getMessage());
        }
        
        return analysis.toString();
    }

    /**
     * Determines the primary data type from a set of values.
     */
    private String determineFieldType(Set<Object> values) {
        if (values.isEmpty()) {
            return "Unknown";
        }
        
        // Check if all values are of the same type
        Set<Class<?>> types = values.stream()
            .map(Object::getClass)
            .collect(Collectors.toSet());
        
        if (types.size() == 1) {
            Class<?> type = types.iterator().next();
            return type.getSimpleName();
        }
        
        return "Mixed types";
    }

    /**
     * Creates an EntityContext for data operations using the participant injected during construction.
     */
    private DefaultEntityContext createEntityContext() {
        return new DefaultEntityContext(participant);
    }

    /**
     * Helper class to track field statistics.
     */
    private static class FieldStats {
        private int totalValues = 0;
        private int nullValues = 0;
        private Set<Object> uniqueValues = new HashSet<>();
        private Class<?> primaryType = null;
        
        public void addValue(Object value) {
            totalValues++;
            if (value == null) {
                nullValues++;
            } else {
                uniqueValues.add(value);
                if (primaryType == null) {
                    primaryType = value.getClass();
                }
            }
        }
        
        @Override
        public String toString() {
            return String.format("Type: %s, Unique values: %d, Nulls: %d/%d", 
                primaryType != null ? primaryType.getSimpleName() : "Unknown",
                uniqueValues.size(), 
                nullValues, 
                totalValues);
        }
    }
}