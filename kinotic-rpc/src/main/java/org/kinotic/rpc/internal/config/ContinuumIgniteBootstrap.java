

package org.kinotic.rpc.internal.config;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteSpring;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Created by navid on 10/15/19
 */
@Configuration
@ConditionalOnProperty(
        value="continuum.disableClustering",
        havingValue = "false",
        matchIfMissing = true)
public class ContinuumIgniteBootstrap {

    @Autowired
    private IgniteConfiguration configuration;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(destroyMethod = "close")
    Ignite ignite() throws IgniteCheckedException {
        return IgniteSpring.start(configuration, applicationContext);
    }

}
