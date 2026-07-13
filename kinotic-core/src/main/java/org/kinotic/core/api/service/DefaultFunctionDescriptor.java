

package org.kinotic.core.api.service;

import java.lang.reflect.Method;

/**
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
class DefaultFunctionDescriptor implements FunctionDescriptor{

    private final String name;

    private final Method invocationMethod;

    public DefaultFunctionDescriptor(String name, Method invocationMethod) {
        this.name = name;
        this.invocationMethod = invocationMethod;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Method invocationMethod() {
        return invocationMethod;
    }
}
