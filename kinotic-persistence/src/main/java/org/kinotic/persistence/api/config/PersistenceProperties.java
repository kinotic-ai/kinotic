package org.kinotic.persistence.api.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PersistenceProperties {

    /**
     * Most cached {@code EntityService} instances to keep, one per active {@code EntityDefinition}.
     * Past this the cache evicts and the next request reloads the definition from Elasticsearch.
     */
    @Min(1)
    private int entityServiceCacheMaxSize = 10_000;

    /**
     * Most cached {@code QueryExecutor} instances to keep, one per named query per
     * {@code EntityDefinition}. Past this the cache evicts and the next call rebuilds the executor.
     */
    @Min(1)
    private int namedQueriesCacheMaxSize = 10_000;

}
