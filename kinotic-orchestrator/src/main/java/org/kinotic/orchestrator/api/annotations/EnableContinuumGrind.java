

package org.kinotic.orchestrator.api.annotations;

import org.kinotic.orchestrator.api.Task;
import org.kinotic.orchestrator.internal.api.config.ContinuumGrindConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used on a Spring Boot application to enable the Continuum Grind.
 *
 * Continuum Grind provides a generic {@link Task} abstraction that supports Autowiring
 *
 *
 * Created by Navid Mitchell 🤪 on 2/10/20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ContinuumGrindConfiguration.class)
public @interface EnableContinuumGrind {

}
