package org.kinotic.core.internal;

import java.lang.reflect.Method;

/**
 * A {@link org.kinotic.core.api.annotations.Consumer} method bound to the bean instance it is
 * invoked on when an event arrives.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
record MethodTarget(Object bean, Method method) {
}
