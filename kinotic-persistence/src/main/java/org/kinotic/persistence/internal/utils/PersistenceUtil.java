package org.kinotic.persistence.internal.utils;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.EntityDescriptor;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    /**
     * Validates the tenants an {@link EntityContext} acts on against the multi-tenancy of an {@link EntityDescriptor}.
     * The tenant selection must already be applied to the context. A participant that belongs to a tenant may select
     * only that tenant, and a participant without one must select the tenants of a {@link MultiTenancyType#SHARED} entity.
     *
     * @param entityDescriptor the entity the operation acts on
     * @param context          the context of the operation
     * @return a {@link Future} that succeeds when the context is valid, and fails with an
     *         {@link AuthorizationException} when the participant selects a tenant other than its own, or an
     *         {@link IllegalArgumentException} when the entity does not accept the context
     */
    public static Future<Void> validateEntityContext(EntityDescriptor entityDescriptor, EntityContext context){
        Future<Void> ret;
        // Continuum allows any published service to be called, so the admin service can reach an
        // EntityDefinition that never enabled multi-tenant selection; the selection is refused here
        if(context.hasTenantSelection() && !entityDescriptor.isMultiTenantSelectionEnabled()){
            ret = Future.failedFuture(
                    new IllegalArgumentException("Multi-tenant access for this EntityDefinition %s is not enabled".formatted(
                            entityDescriptor.name()))
            );
        }else if(entityDescriptor.multiTenancyType() != MultiTenancyType.SHARED){
            ret = Future.succeededFuture();
        }else if(context.getTenantId() != null){
            // A participant that belongs to a tenant is confined to it, whatever selection it asks for
            if(context.hasTenantSelection()
                    && !List.of(context.getTenantId()).equals(context.getTenantSelection())){
                ret = Future.failedFuture(new AuthorizationException("Participant may only select its own tenant"));
            }else{
                ret = Future.succeededFuture();
            }
        }else if(context.hasTenantSelection()){
            ret = Future.succeededFuture();
        }else{
            ret = Future.failedFuture(new IllegalArgumentException("Participant with a TenantId is required when MultiTenancyType is SHARED"));
        }
        return ret;
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
