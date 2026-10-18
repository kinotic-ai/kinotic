package org.kinotic.domain.api.model;

/**
 * What a published site's hostname names: the application the site belongs to, the UI it serves, and the
 * tenant it is addressed to when its first label carries one.
 *
 * @param application the application the site belongs to
 * @param uiName      the name of the application's UI the site serves
 * @param tenantId    the tenant the site is addressed to, or {@code null} for the application's own site
 */
public record SiteAddress(ApplicationKey application, String uiName, String tenantId) {
}
