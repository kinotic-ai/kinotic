package org.kinotic.core.internal.api.fabric;

/**
 * One {@link org.kinotic.core.api.annotations.Consumer} method's membership in the {@link Downlink}
 * for its event type, recorded in {@link BeanWiring} so unwiring can remove exactly this target.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
record ConsumerRegistration(Class<?> eventType, MethodTarget target) {
}
