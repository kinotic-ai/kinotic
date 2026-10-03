package org.kinotic.core.api;

/**
 * An Ignite node attribute that a module declares as a bean. Every node running the module carries the attribute,
 * so the cluster can select the nodes that run it.
 *
 * @param name  the attribute name, prefixed with {@code kinotic.} and the module's name
 * @param value the attribute value, which must be serializable
 */
public record NodeAttribute(String name, Object value) {
}
