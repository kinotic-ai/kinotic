package org.kinotic.rpc.api.annotations;

import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

/**
 * {@link Proxy} annotations mark an interface as a proxy to a {@link Publish}ed service.
 * They will automatically be detected when the spring application boots if any exist in the {@link KinoticRpcPackages}.
 *
 * Created by Navid Mitchell on 2019-02-03.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed // TODO: do we need this ?
public @interface Proxy {

    /**
     * The logical namespace that can be used to locate the published service
     * If this is not provided, the class package is used
     */
    String namespace() default "";

    /**
     * The logical name that can be used to locate the published service
     * If this is not provided, the class name is used
     */
    String name() default "";

}
