package org.kinotic.core.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link Zones} declares the zones a {@link Publish}ed service is addressable in, or the zone a
 * {@link Proxy} targets. A zone is the validated leading portion of the service CRI's resourceName;
 * a service declaring multiple zones registers one address per zone.
 *
 * May be placed on the service type or on a package via package-info.java to apply to every
 * {@link Publish}ed interface in that package. A type-level declaration overrides the package's.
 * When neither is present a service defaults to the {@code system} zone.
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Zones {

    /**
     * The zones the service registers under. Each zone is one or more dot separated labels of
     * lowercase letters, digits, and interior dashes, e.g. {@code api} or {@code api.admin}
     */
    String[] value();

}
