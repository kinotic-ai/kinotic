package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.List;

/**
 * The schema captured for one service: its {@link ServiceDefinition} plus every complex type the service's
 * functions reference.
 */
public record ServiceSchema(ServiceDefinition serviceDefinition, List<ComplexC3Type> referencedTypes) {
}
