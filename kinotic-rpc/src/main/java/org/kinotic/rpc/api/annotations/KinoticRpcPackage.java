package org.kinotic.rpc.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Indicates that the package containing the annotated class should be registered with
 * {@link KinoticRpcPackages}.
 * This must be used with the {@link org.springframework.context.annotation.Configuration} annotation, or else it will not be detected.
 *
 *
 * Created by navid on 2/11/20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(KinoticRpcPackages.Registrar.class)
public @interface KinoticRpcPackage {
}
