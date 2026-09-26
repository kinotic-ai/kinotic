package org.kinotic.domain.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration for how entity data is stored, shared by the services that publish
 * {@code EntityDefinition}s and the services that read and write their entities.
 * Bound under {@code kinotic.domain.persistence.*}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class DomainPersistenceProperties {

    /**
     * The document field that holds the tenant id of an entity whose multi-tenancy type is
     * {@code SHARED} and that does not declare its own tenant id field. Publishing an
     * {@code EntityDefinition} adds this field to its index mapping, so set it before the first
     * such {@code EntityDefinition} is published, and to the same value on every server.
     */
    @NotBlank
    private String tenantIdFieldName = "tenantId";

}
