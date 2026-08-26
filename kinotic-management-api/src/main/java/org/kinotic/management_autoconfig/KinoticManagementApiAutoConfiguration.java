package org.kinotic.management_autoconfig;

import org.kinotic.management.KinoticManagementApiLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(KinoticManagementApiLibrary.class)
public class KinoticManagementApiAutoConfiguration {

}
