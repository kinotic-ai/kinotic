package org.kinotic.management.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.EntityDecorator;
import org.kinotic.domain.api.model.persistence.idl.decorators.FlattenedDecorator;
import org.kinotic.domain.api.model.persistence.idl.decorators.IdDecorator;
import org.kinotic.domain.api.model.persistence.idl.decorators.PolicyDecorator;
import org.kinotic.idl.api.converter.C3ConversionException;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.internal.api.converter.DefaultIdlConverterFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers which field policies an entity's Elasticsearch mapping keeps for authorization.
 */
class DefaultEntityDefinitionConversionServiceTest {

    private final DefaultEntityDefinitionConversionService conversionService =
            new DefaultEntityDefinitionConversionService(new DefaultIdlConverterFactory(), new DomainPersistenceProperties());

    @Test
    void aPolicyOnAFieldOfAFlattenedObjectIsRejected() {
        ObjectC3Type details = new ObjectC3Type().setName("Details").setNamespace("test");
        details.addProperty("salary", new StringC3Type(), List.of(policy()));

        EntityDefinition definition = entity(details, List.of(new FlattenedDecorator()));

        C3ConversionException rejection = assertThrows(C3ConversionException.class,
                                                       () -> conversionService.convertToElasticMapping(definition));
        assertInstanceOf(IllegalArgumentException.class, rejection.getCause());
        assertEquals("A Policy cannot be applied to a field of a Flattened object, at: details.salary", rejection.getCause().getMessage());
    }

    @Test
    void aPolicyOnAFlattenedFieldItselfIsKept() {
        ObjectC3Type details = new ObjectC3Type().setName("Details").setNamespace("test");
        details.addProperty("salary", new StringC3Type());

        ElasticConversionResult result = conversionService.convertToElasticMapping(
                entity(details, List.of(new FlattenedDecorator(), policy())));

        assertEquals(1, result.decoratedProperties().stream()
                                .filter(property -> property.getJsonPath().equals("details")
                                        && property.findDecorator(PolicyDecorator.class) != null)
                                .count());
    }

    private static EntityDefinition entity(ObjectC3Type details, List<C3Decorator> decorators) {
        ObjectC3Type schema = new ObjectC3Type().setName("Person").setNamespace("test");
        schema.addDecorator(new EntityDecorator());
        schema.addProperty("id", new StringC3Type(), List.of(new IdDecorator()));
        schema.addProperty("details", details, decorators);
        return new EntityDefinition().setName("Person").setSchema(schema);
    }

    private static PolicyDecorator policy() {
        return new PolicyDecorator().setPolicies(List.of(List.of("hr")));
    }
}
