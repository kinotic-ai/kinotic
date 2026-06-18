package org.kinotic.sql.executor.executors;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateLifecyclePolicyStatement;
import org.kinotic.sql.domain.statements.RolloverConditions;
import org.kinotic.sql.executor.StatementExecutor;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import lombok.RequiredArgsConstructor;

/**
 * Executes CREATE LIFECYCLE POLICY statements against Elasticsearch.
 * Creates a reusable ILM policy with an optional hot-phase rollover and a delete phase that ages data out.
 * Created by Navíd Mitchell 🤝 Grok on 6/18/26.
 */
@Component
@RequiredArgsConstructor
public class CreateLifecyclePolicyStatementExecutor implements StatementExecutor<CreateLifecyclePolicyStatement, Void> {
    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof CreateLifecyclePolicyStatement;
    }

    @Override
    public CompletableFuture<Void> executeMigration(CreateLifecyclePolicyStatement statement) {
        return client.ilm().putLifecycle(p -> p
                .name(statement.policyName())
                .policy(policy -> policy.phases(phases -> {
                    RolloverConditions rollover = statement.rollover();
                    if (rollover != null) {
                        phases.hot(hot -> hot.actions(actions -> actions.rollover(ro -> {
                            if (rollover.maxAge() != null) {
                                ro.maxAge(age -> age.time(rollover.maxAge()));
                            }
                            if (rollover.maxSize() != null) {
                                ro.maxSize(rollover.maxSize());
                            }
                            if (rollover.maxDocs() != null) {
                                ro.maxDocs(rollover.maxDocs().longValue());
                            }
                            return ro;
                        })));
                    }
                    if (statement.deleteMinAge() != null) {
                        phases.delete(delete -> delete
                                .minAge(age -> age.time(statement.deleteMinAge()))
                                .actions(actions -> actions.delete(d -> d)));
                    }
                    return phases;
                }))
        ).thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Void> executeQuery(CreateLifecyclePolicyStatement statement, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("CREATE LIFECYCLE POLICY not supported as a named query");
    }
}
