package org.kinotic.continuum.idl_autoconfig;

import org.kinotic.continuum.idl.ContinuumIdlLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(ContinuumIdlLibrary.class)
public class ContinuumIdlAutoConfiguration {

}
