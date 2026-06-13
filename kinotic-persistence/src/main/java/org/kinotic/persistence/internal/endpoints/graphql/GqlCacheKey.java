package org.kinotic.persistence.internal.endpoints.graphql;

/**
 * Cache key for the GraphQL schema handler cache, keyed by organization and application.
 */
public record GqlCacheKey(String organizationId, String applicationId) {}
