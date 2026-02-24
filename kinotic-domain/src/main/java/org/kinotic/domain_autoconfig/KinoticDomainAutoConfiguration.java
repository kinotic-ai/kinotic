package org.kinotic.domain_autoconfig;

import org.kinotic.domain.KinoticDomainLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(KinoticDomainLibrary.class)
public class KinoticDomainAutoConfiguration {

}
