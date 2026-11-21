package org.kinotic.idl.internal.directory;

/**
 * The resource a function's check is made on, as {@link AuthzDecorators} derives it.
 *
 * @param resource           the type of the object checked
 * @param resourceId         the template of the checked object's id
 * @param permissionResource the type the permission is about: the function's own type for a check made on its
 *                           parent, the checked type otherwise
 */
record CheckedResource(String resource, String resourceId, String permissionResource) {
}
