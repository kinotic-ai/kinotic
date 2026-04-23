package org.kinotic.persistence.internal.endpoints.graphql;

import com.github.benmanes.caffeine.cache.AsyncCache;
import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;

/**
 * A PreparsedDocumentProvider that caches the results of parsing and validating a query.
 * Created by Navíd Mitchell 🤪 on 4/17/23.
 */
public class CachingPreparsedDocumentProvider implements PreparsedDocumentProvider {

    private final AsyncCache<String, PreparsedDocumentEntry> cache;

    public CachingPreparsedDocumentProvider(DefaultCaffeineCacheFactory cacheFactory) {
        this.cache = cacheFactory.<String, PreparsedDocumentEntry>newBuilder()
                .name("preparsedDocumentCache")
                .expireAfterWrite(Duration.ofHours(2))
                .maximumSize(1000)
                .buildAsync();
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput,
                                                                      Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        Function<String, PreparsedDocumentEntry> mapCompute = key -> parseAndValidateFunction.apply(executionInput);
        return cache.get(executionInput.getQuery(), mapCompute);
    }

}
