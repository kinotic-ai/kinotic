package org.kinotic.idl.api.directory;

/**
 * The class pair a service registers with: the {@code @Publish} interface deciding which functions exist, and
 * the implementation whose most specific methods decide parameter names, generic bindings, and annotations.
 * Pass the interface itself as the implementation when no separate implementation exists.
 */
public record DirectoryRegistration(Class<?> serviceInterface, Class<?> serviceImplementation) {
}
