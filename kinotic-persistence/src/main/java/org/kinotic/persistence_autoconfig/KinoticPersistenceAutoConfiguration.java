package org.kinotic.persistence_autoconfig;

import org.kinotic.persistence.KinoticPersistenceLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(KinoticPersistenceLibrary.class)
// the node attributes a node running this module advertises, merged into kinotic.ignite.nodeAttributes; the source
// joins the environment as this class is parsed, so a bean binds it but a condition evaluated earlier does not see it
@PropertySource("classpath:META-INF/kinotic-persistence.properties")
public class KinoticPersistenceAutoConfiguration {

}
