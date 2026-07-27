package org.kinotic.core.internal.config;

import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Ignite configuration honors the same {@code kinotic.disableClustering} flag as
 * {@link KinoticIgniteBootstrap}, which consumes the {@link IgniteConfiguration} it defines.
 */
public class KinoticIgniteConfigTest {

    private final ApplicationContextRunner runner
            = new ApplicationContextRunner()
                    .withUserConfiguration(PropertiesConfig.class, KinoticIgniteConfig.class)
                    // the SHAREDFS default would create its discovery directory on disk
                    .withPropertyValues("kinotic.ignite.discoveryType=LOCAL");

    @Test
    public void clusteringEnabledByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(IgniteConfiguration.class));
    }

    @Test
    public void clusteringDisabledRemovesTheIgniteConfiguration() {
        // a property name no bean binds would leave the configuration active here, since
        // the ConditionalOnProperty matches when it is missing
        runner.withPropertyValues("kinotic.disableClustering=true")
              .run(context -> assertThat(context).doesNotHaveBean(IgniteConfiguration.class));
    }

    @Test
    public void unrelatedDisableFlagsDoNotRemoveTheIgniteConfiguration() {
        runner.withPropertyValues("continuum.disableClustering=true")
              .run(context -> assertThat(context).hasSingleBean(IgniteConfiguration.class));
    }

    // KinoticIgniteConfig field-injects KinoticProperties, which carries the flag the
    // ConditionalOnProperty above it reads
    @EnableConfigurationProperties(KinoticProperties.class)
    static class PropertiesConfig {
    }

}
