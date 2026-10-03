package org.kinotic.core;

import org.kinotic.core.api.security.Participant;
import org.kinotic.idl.api.directory.SkippedParameterTypes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * This class provides the necessary configuration annotations to enable this library for use in Spring boot applications
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
public class KinoticCoreLibrary {

    /**
     * The parameter types the platform supplies to a function, which its contract therefore leaves out. The
     * argument resolvers bind a {@link Participant} parameter from the security context by the same rule.
     */
    @Bean
    public SkippedParameterTypes participantParameterTypes() {
        return new SkippedParameterTypes(Set.of(Participant.class));
    }

}