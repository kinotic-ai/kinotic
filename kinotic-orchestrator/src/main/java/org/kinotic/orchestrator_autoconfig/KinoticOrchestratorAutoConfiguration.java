package org.kinotic.orchestrator_autoconfig;

import org.kinotic.orchestrator.KinoticOrchestratorLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library.
 * It is defined in a separate package because it must not be scanned by the spring context.
 */
@AutoConfiguration
@Import(KinoticOrchestratorLibrary.class)
public class KinoticOrchestratorAutoConfiguration {

}
