package org.kinotic.core.internal.api.support;

import org.springframework.stereotype.Component;

@Component
public class DefaultScopedOnlyRpcTestService implements ScopedOnlyRpcTestService {

    @Override
    public String nodeId() {
        return NODE_ID;
    }

    @Override
    public String instanceValue() {
        return "scoped-only";
    }

}
