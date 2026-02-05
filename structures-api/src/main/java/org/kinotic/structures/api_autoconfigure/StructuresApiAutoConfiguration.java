package org.kinotic.structures.api_autoconfigure;

import org.kinotic.structures.api.StructuresApiLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for the payment api library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(StructuresApiLibrary.class)
public class StructuresApiAutoConfiguration {

}
