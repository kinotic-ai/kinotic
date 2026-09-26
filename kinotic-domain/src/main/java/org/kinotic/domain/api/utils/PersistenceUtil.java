package org.kinotic.domain.api.utils;

import org.kinotic.domain.api.model.persistence.EntityDefinition;

import java.util.regex.Pattern;

public class PersistenceUtil {

    private static final Pattern EntityDefinitionNamePattern = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * Builds an {@link EntityDefinition} id of the shape
     * {@code <organizationId>.<applicationId>.<entityDefinitionName>}, lowercased.
     *
     * @param organizationId of the Organization the definition belongs to
     * @param applicationId of the Application the definition belongs to
     * @param entityDefinitionName the definition's name
     * @return the {@link EntityDefinition} id
     */
    public static String createEntityDefinitionId(String organizationId, String applicationId, String entityDefinitionName){
        return (organizationId + "." + applicationId + "." + entityDefinitionName).toLowerCase();
    }

    /**
     * Function will validate a {@link EntityDefinition}
     *
     * @param entityDefinition to validate
     * @throws IllegalArgumentException will be thrown if the {@link EntityDefinition} is invalid
     */
    public static void validateEntityDefinition(EntityDefinition entityDefinition){

        validateEntityDefinitionName(entityDefinition.getName());

        DomainUtil.validateApplicationId(entityDefinition.getApplicationId());

        DomainUtil.validateProjectId(entityDefinition.getProjectId());

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

}
