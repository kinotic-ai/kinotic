package org.kinotic.persistence.internal.utils;

import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.utils.CoreUtil;
import org.kinotic.persistence.api.model.EntityDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class PersistenceUtil {

    private static final Pattern EntityDefinitionNamePattern = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * Function will convert a {@link EntityDefinition} applicationId and name to a valid
     * @param applicationId to convert
     * @param entityDefinitionName to convert
     * @return a valid {@link EntityDefinition} id
     */
    public static String entityDefinitionNameToId(String applicationId, String entityDefinitionName){
        return (applicationId + "." + entityDefinitionName).toLowerCase();
    }

    /**
     * Function will validate a {@link EntityDefinition}
     *
     * @param entityDefinition to validate
     * @throws IllegalArgumentException will be thrown if the {@link EntityDefinition} is invalid
     */
    public static void validateEntityDefinition(EntityDefinition entityDefinition){

        validateEntityDefinitionName(entityDefinition.getName());

        CoreUtil.validateApplicationId(entityDefinition.getApplicationId());

        CoreUtil.validateProjectId(entityDefinition.getProjectId());

        if (entityDefinition.getSchema() == null) {
            throw new IllegalArgumentException("EntityDefinition schema must not be null");
        }
    }

    /**
     * Function will validate the {@link EntityDefinition} name
     *
     * @param entityDefinitionName to validate
     * @throws IllegalArgumentException will be thrown if the {@link EntityDefinition} name is invalid
     */
    public static void validateEntityDefinitionName(String entityDefinitionName){
        if(entityDefinitionName == null){
            throw new IllegalArgumentException("EntityDefinition name must not be null");
        }
        if (!EntityDefinitionNamePattern.matcher(entityDefinitionName).matches()){
            throw new IllegalArgumentException("EntityDefinition Name Invalid, first character must be a " +
                                               "letter, number or underscore. And contain only letters, numbers or underscores. Got "+ entityDefinitionName);
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
        if(!EntityDefinitionNamePattern.matcher(propertyName).matches()){
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
