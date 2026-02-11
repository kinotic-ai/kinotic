package org.kinotic.persistence.internal.utils;

import org.apache.commons.lang3.Validate;
import org.kinotic.structures.api.domain.Structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class StructuresUtil {

    private static final Pattern StructureNamePattern = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern StructureApplicationPattern = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]*$");
    private static final Pattern StructureProjectIdPattern = Pattern.compile("^[a-z][a-z0-9._-]*$");

    /**
     * Function will convert a structure applicationId and name to a valid
     * @param structureApplicationId to convert
     * @param structureName to convert
     * @return a valid structure id
     */
    public static String structureNameToId(String structureApplicationId, String structureName){
        return (structureApplicationId + "." + structureName).toLowerCase();
    }

    /**
     * Function will validate a structure
     *
     * @param structure to validate
     * @throws IllegalArgumentException will be thrown if the structure is invalid
     */
    public static void validateStructure(Structure structure){

        validateStructureName(structure.getName());

        validateApplicationId(structure.getApplicationId());

        validateProjectId(structure.getProjectId());

        if (structure.getEntityDefinition() == null) {
            throw new IllegalArgumentException("Structure entityDefinition must not be null");
        }
    }

    /**
     * Function will validate the structure name
     *
     * @param structureName to validate
     * @throws IllegalArgumentException will be thrown if the structure name is invalid
     */
    public static void validateStructureName(String structureName){
        if(structureName == null){
            throw new IllegalArgumentException("Structure name must not be null");
        }
        if (!StructureNamePattern.matcher(structureName).matches()){
            throw new IllegalArgumentException("Structure Name Invalid, first character must be a " +
                                               "letter, number or underscore. And contain only letters, numbers or underscores. Got "+ structureName);
        }
    }

    /**
     * Function will validate the structure application name
     *
     * @param applicationId to validate
     * @throws IllegalArgumentException will be thrown if the structure application is invalid
     */
    public static void validateApplicationId(String applicationId) {
        if (applicationId == null) {
            throw new IllegalArgumentException("Application Id must not be null");
        }
        if (!StructureApplicationPattern.matcher(applicationId).matches()){
            throw new IllegalArgumentException("Structure Application Id Invalid, first character must be a " +
                                               "letter. And contain only letters, numbers, periods, underscores or dashes. Got "+ applicationId);
        }
    }

    public static void validateProjectId(String projectId){
        if(projectId == null){
            throw new IllegalArgumentException("Project Id must not be null");
        }
        if (!StructureProjectIdPattern.matcher(projectId).matches()){
            throw new IllegalArgumentException("Structure Project Id Invalid, first character must be a " +
                                               "letter. And contain only letters, numbers, periods, underscores or dashes. Got "+ projectId);
        }
    }

    /**
     * Function will validate the property name
     *
     * @param propertyName to validate
     * @throws IllegalArgumentException will be thrown if the property name is invalid
     */
    public static void validatePropertyName(String propertyName){
        if(propertyName == null){
            throw new IllegalArgumentException("Property Name must not be null");
        }
        if(propertyName.length() > 255){
            throw new IllegalArgumentException("Property Name cannot have more than 255 characters");
        }
        if(!StructureNamePattern.matcher(propertyName).matches()){
            throw new IllegalArgumentException("Property Name Invalid, first character must be a " +
                                               "letter, number or underscore. And contain only letters, numbers or underscores. Got "+ propertyName);
        }
    }

    /**
     * Function will convert a List to a Map using the provided mapping function.
     * @param list to convert
     * @param mappingFunction to use derive the key from the value
     * @return a map of the list
     * @param <K> the type of the key
     * @param <T> the type of the list
     * @throws IllegalArgumentException if multiple values map to the same key
     */
    public static <K, T> Map<K, T> listToMap(List<T> list, Function<T, K> mappingFunction){
        Validate.notNull(list, "list cannot be null");
        Map<K, T> ret = new LinkedHashMap<>(list.size());
        for(T value : list){
            K key = mappingFunction.apply(value);
            if(ret.containsKey(key)){
                T existing = ret.get(key);
                throw new IllegalArgumentException("Multiple values that map to the same key: " + key
                                                           + "\n existing: " + existing.getClass().getName() + " new: " + value.getClass().getName());
            }
            ret.put(key, value);
        }
        return ret;
    }

}
