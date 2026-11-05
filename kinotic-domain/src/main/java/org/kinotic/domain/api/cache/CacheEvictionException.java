package org.kinotic.domain.api.cache;

/**
 * A cache eviction an app server did not acknowledge, raised once the change the eviction is for is written, so
 * the caller knows the write landed and only the cache may be behind.
 */
public class CacheEvictionException extends IllegalStateException {

    public CacheEvictionException(CacheEvictionEvent event, Throwable cause) {
        super("The change to " + describe(event) + " was written, but an app server did not acknowledge the eviction"
              + " of its cache: " + cause.getMessage(), cause);
    }

    private static String describe(CacheEvictionEvent event) {
        String id = event.getEvictionSourceType() == EvictionSourceType.NAMED_QUERY
                ? event.getNamedQueryId()
                : event.getEntityDefinitionId();
        return event.getEvictionSourceType().getDisplayName().toLowerCase() + " " + id;
    }

}
