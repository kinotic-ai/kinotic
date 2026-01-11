package org.mindignited.structures.api;

import org.kinotic.continuum.api.annotations.EnableContinuum;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * This class provides the needed configuration annotations to enable this library for use in Continuum applications
 */
@Configuration
@ComponentScan
@EnableContinuum
public class StructuresApiLibrary {
}
