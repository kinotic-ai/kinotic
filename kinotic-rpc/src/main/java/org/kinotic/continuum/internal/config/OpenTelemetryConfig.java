package org.kinotic.continuum.internal.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by Navíd Mitchell 🤪 on 10/9/24.
 */
@Configuration
public class OpenTelemetryConfig {

    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

}
