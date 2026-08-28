package org.kinotic.grindv2_autoconfig;

import org.kinotic.grindv2.KinoticGrindLibrary;
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
