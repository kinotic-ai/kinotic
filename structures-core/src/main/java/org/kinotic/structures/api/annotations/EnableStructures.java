

package org.kinotic.structures.api.annotations;

import org.kinotic.structures.internal.config.StructuresConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Annotation to be used on a Spring Boot application to enable Structures
 * Created by Nicholas Padilla 😈 on 07/30/19.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(StructuresConfiguration.class)
public @interface EnableStructures {
}
