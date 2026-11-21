package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.directory.AuthzPermissionResolver;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A permission resolver a report's query is checked with: reading for a query that reads, deleting for one that
 * deletes.
 */
@Component
public class TestStatementPermissionResolver implements AuthzPermissionResolver {

    public static final String NAME = "test_statement";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<String> parameters() {
        return List.of("reportId", "queryName");
    }

    @Override
    public List<AuthzPermissionChoice> possiblePermissions() {
        return List.of(new AuthzPermissionChoice("can_read", "the query reads rows"),
                       new AuthzPermissionChoice("can_delete", "the query is a DELETE"));
    }
}
