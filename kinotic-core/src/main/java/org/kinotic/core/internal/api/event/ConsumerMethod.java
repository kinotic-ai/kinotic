package org.kinotic.core.internal.api.event;

import java.lang.reflect.Method;

/**
 * A {@link org.kinotic.core.api.annotations.Consumer} method bound to the bean instance it is invoked on when an
 * event arrives.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
record ConsumerMethod(Object bean, Method method) {
}
