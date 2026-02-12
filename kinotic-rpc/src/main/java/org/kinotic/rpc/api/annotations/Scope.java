

package org.kinotic.rpc.api.annotations;

import java.lang.annotation.*;

/**
 * {@link Scope} denotes that a service will have more than one instance available.
 * The {@link Scope} can then be used to resolve a particular instance of a service.
 *
 * If {@link Scope} is used on a service that is published via {@link Publish} then the {@link Scope} identifies that service instance.
 * If {@link Scope} is used on a method parameter of a {@link Proxy} the {@link Scope} is used to determine which service instance rpc requests should be routed to.
 *
 *
 * Created by Navid Mitchell on 2019-01-18.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scope {
}
