package org.kinotic.core.internal.api.event.fabric;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * A {@link org.kinotic.core.api.annotations.Consumer} method bound to the bean instance it is
 * invoked on when an event arrives.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
record MethodTarget(Object bean, Method method) implements ConsumerTarget {

    @Override
    public void accept(Object element) {
        ReflectionUtils.invokeMethod(method, bean, element);
    }

    @Override
    public String toString() {
        return method.toString();
    }

}
