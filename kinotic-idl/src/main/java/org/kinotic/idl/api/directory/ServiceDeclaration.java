package org.kinotic.idl.api.directory;

/**
 * The class pair a {@code ServiceDefinition} is created from: the {@code @Publish} interface deciding which
 * functions exist, and the implementation whose most specific methods decide parameter names, generic
 * bindings, and annotations. Pass the interface itself as the implementation when no separate
 * implementation exists.
 */
public record ServiceDeclaration(Class<?> serviceInterface, Class<?> serviceImplementation) {
}
