package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.core.internal.api.ScopedRpcTests;

/**
 * Scoped service with no ScopeOptional methods, used by the {@link ScopedRpcTests} to prove a
 * service that never opted in keeps its scoped-only addressability.
 */
@Publish
public interface AffineRpcTestService {

    String NODE_ID = "affine-test-node";

    @Scope
    String nodeId();

    String instanceValue();

}
