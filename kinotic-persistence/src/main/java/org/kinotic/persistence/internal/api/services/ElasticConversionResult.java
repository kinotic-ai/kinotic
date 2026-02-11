package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import org.kinotic.persistence.api.domain.idl.decorators.EntityDecorator;
import org.kinotic.persistence.api.domain.DecoratedProperty;

import java.util.List;

/**
 * This is the result produced by the {@link StructureConversionService}
 * Created by Navíd Mitchell 🤪 on 5/11/23.
 *
 * @param decoratedProperties A list of all {@link DecoratedProperty} that were found during the conversion process
 * @param entityDecorator     The {@link EntityDecorator} that was found while converting the structure
 * @param objectProperty      The root object property that represents the converted C3Type
 * @param versionFieldName    The name of the field that will be used for optimistic locking or null if optimistic locking is not enabled
 * @param tenantIdFieldName   The name of the field that will be used to hold the tenant id for an entity
 */
public record ElasticConversionResult(List<DecoratedProperty> decoratedProperties,
                                      EntityDecorator entityDecorator,
                                      ObjectProperty objectProperty,
                                      String versionFieldName,
                                      String tenantIdFieldName,
                                      String timeReferenceFieldName) {
}
