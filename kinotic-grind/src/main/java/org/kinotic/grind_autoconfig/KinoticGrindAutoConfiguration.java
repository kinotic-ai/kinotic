package org.kinotic.grind_autoconfig;

import org.kinotic.grind.KinoticGrindLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library.
 * It is defined in a separate package because it must not be scanned by the spring context.
 */
@AutoConfiguration
@Import(KinoticGrindLibrary.class)
public class KinoticGrindAutoConfiguration {

}
