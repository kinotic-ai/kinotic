package org.kinotic.core_autoconfig;

import org.kinotic.core.KinoticCoreLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(KinoticCoreLibrary.class)
public class KinoticCoreAutoConfiguration {

}
