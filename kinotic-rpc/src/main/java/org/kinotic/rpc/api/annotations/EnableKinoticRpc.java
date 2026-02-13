package org.kinotic.rpc.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used on a Spring Boot Application / Library to enable Kinotic RPC
 *
 * You should only include one {@link EnableKinoticRpc} annotation per application or library
 *
 * Created by Navid Mitchell 🤪 on 11/28/18.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@KinoticRpcPackage
public @interface EnableKinoticRpc {

    /**
     * The default version to use for all published services.
     * FIXME: Implement this
     */
    String version() default "";

}
