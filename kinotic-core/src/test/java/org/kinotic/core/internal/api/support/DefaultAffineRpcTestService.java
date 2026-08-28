package org.kinotic.core.internal.api.support;

import org.springframework.stereotype.Component;

@Component
public class DefaultAffineRpcTestService implements AffineRpcTestService {

    @Override
    public String nodeId() {
        return NODE_ID;
    }

    @Override
    public String instanceValue() {
        return "affine";
    }

}
