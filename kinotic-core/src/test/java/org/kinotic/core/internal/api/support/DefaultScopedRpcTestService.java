package org.kinotic.core.internal.api.support;

import org.springframework.stereotype.Component;

@Component
public class DefaultScopedRpcTestService implements ScopedRpcTestService {

    @Override
    public String nodeId() {
        return NODE_ID;
    }

    @Override
    public String anyInstanceValue() {
        return ANY_INSTANCE_VALUE;
    }

    @Override
    public String instanceValue() {
        return INSTANCE_VALUE;
    }

}
