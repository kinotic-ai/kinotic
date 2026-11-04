package org.kinotic.ee.internal.license;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * Matches when the server's Kinotic Enterprise License key turns on the feature a
 * {@link ConditionalOnEnterpriseFeature} names.
 */
public class OnEnterpriseFeatureCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnEnterpriseFeature.class.getName());
        EnterpriseFeature feature = (EnterpriseFeature) attributes.get("value");
        License license = LicenseVerifier.licenseOf(context.getEnvironment());
        return license != null && license.allows(feature);
    }
}
