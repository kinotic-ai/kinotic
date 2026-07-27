package org.kinotic.core.internal.api.directory;

/**
 * The class pair a service registers with: the {@code @Publish} contract deciding which functions exist, and
 * the implementation whose most specific methods decide parameter names, generic bindings, and annotations.
 */
public record DirectoryRegistration(Class<?> contract, Class<?> implementation) {
}
