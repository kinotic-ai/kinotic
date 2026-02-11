package org.kinotic.persistence.api_autoconfigure;

import org.kinotic.persistence.api.PersistenceApiLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for the payment api library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(PersistenceApiLibrary.class)
public class PersistenceApiAutoConfiguration {

}
