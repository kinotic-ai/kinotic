package org.kinotic.persistence.internal.api.services;

import org.kinotic.persistence.api.model.EntityDefinition;

/**
 * Handles converting {@link EntityDefinition}s to various mappings. Such as ElasticSearch.
 * Created by Navíd Mitchell 🤪on 5/11/23.
 */
public interface EntityDefinitionConversionService {

    /**
     * Converts the given {@link EntityDefinition#getSchema()} to an ElasticSearch ObjectProperty
     * @param entityDefinition to convert
     * @return the {@link ElasticConversionResult} created for the {@link EntityDefinition}
     */
    ElasticConversionResult convertToElasticMapping(EntityDefinition entityDefinition);

}
