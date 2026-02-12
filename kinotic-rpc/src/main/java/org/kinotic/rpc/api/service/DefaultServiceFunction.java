

package org.kinotic.rpc.api.service;

import java.lang.reflect.Method;

/**
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
class DefaultServiceFunction implements ServiceFunction{

    private final String name;

    private final Method invocationMethod;

    public DefaultServiceFunction(String name, Method invocationMethod) {
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
