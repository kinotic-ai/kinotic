package org.mindignited.structures.internal.api.services.impl.security.graphos;

import org.mindignited.structures.api.services.security.graphos.PolicyAuthorizationRequest;

public class DefaultPolicyAuthorizationRequest implements PolicyAuthorizationRequest {
    private final String policy;
    private boolean authorized = false;

    public DefaultPolicyAuthorizationRequest(String policy) {
        this.policy = policy;
    }

    @Override
    public void authorize() {
        authorized = true;
    }

    @Override
    public void deny() {
        authorized = false;
    }

    @Override
    public boolean isAuthorized() {
        return authorized;
    }

    @Override
    public String policy() {
        return policy;
    }
}
