package org.kinotic.persistence.internal.api.services;

import org.kinotic.domain.api.model.RawJson;

/**
 * An entity's document as it was read from Elasticsearch.
 *
 * @param json     the document
 * @param tenantId the tenant the document names in the entity's tenant field, or null if it names none
 */
public record EntityDocument(RawJson json, String tenantId) {
}
