package org.kinotic.domain.internal.api.ee.license;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers the annotated bean only when the server's Kinotic Enterprise License key turns {@link #value()} on.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnEnterpriseFeatureCondition.class)
public @interface ConditionalOnEnterpriseFeature {

    /** The Enterprise Feature the bean provides. */
    EnterpriseFeature value();
}
