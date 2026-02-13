package org.kinotic.rpc.api.annotations;

import org.kinotic.boot.KinoticBootLibrary;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used on a Spring Boot Application / Library to enable Kinotic
 *
 * You should only include one {@link EnableKinotic} annotation per application or library
 *
 * Created by Navid Mitchell 🤪 on 11/28/18.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@KinoticPackage
public @interface EnableKinotic {

    /**
     * The logical name for the Kinotic application / library
     * If this is not provided, the class name is used
     */
    String name() default "";

    /**
     * The version of the Kinotic application / library.
     */
    String version() default "";

}
