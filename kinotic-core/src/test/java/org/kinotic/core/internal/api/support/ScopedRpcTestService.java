package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.core.api.annotations.ScopeOptional;
import org.kinotic.core.internal.api.ScopedRpcTests;

/**
 * Scoped service with one {@link ScopeOptional} method, used by the {@link ScopedRpcTests}
 * to exercise the shared unscoped address.
 */
@Publish
public interface ScopedRpcTestService {

    String NODE_ID = "scoped-test-node";
    String ANY_INSTANCE_VALUE = "any instance can answer this";
    String INSTANCE_VALUE = "only the named instance can answer this";

    @Scope
    String nodeId();

    @ScopeOptional
    String anyInstanceValue();

    String instanceValue();

}
