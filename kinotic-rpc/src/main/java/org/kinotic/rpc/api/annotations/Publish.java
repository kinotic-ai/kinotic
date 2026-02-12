

package org.kinotic.rpc.api.annotations;

import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

/**
 * {@link Publish} denotes that an object should be treated as a public service accessible remotely.
 * This annotation is typically used on an interface and not on the implementing class.
 *
 * Created by Navid Mitchell on 2019-01-18.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed // TODO: do we need this ?
public @interface Publish {

    /**
     * The logical namespace that can be used to locate the published service
     * If this is not provided the class's package is used
     */
    String namespace() default "";

    /**
     * The logical name that can be used to locate the published service
     * If this is not provided the class name is used
     */
    String name() default "";

}
