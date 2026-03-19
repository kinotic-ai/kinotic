package org.kinotic.persistence.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ClusterEvictionProperties {
    
    /**
     * The maximum number of retry attempts for cluster cache sync
     */
    private Integer maxCacheSyncRetryAttempts = 3;

    /**
     * The delay between retry attempts for cluster cache sync
     */
    private Long cacheSyncRetryDelayMs = 1000L; // 1 second

    /**
     * The timeout for cluster cache sync
     */
    private Long cacheSyncTimeoutMs = 30000L; // 30 seconds

    
}
