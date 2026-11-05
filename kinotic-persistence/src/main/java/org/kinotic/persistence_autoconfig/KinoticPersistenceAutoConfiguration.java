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
// the node attributes a node running this module advertises, merged into kinotic.ignite.nodeAttributes
@PropertySource("classpath:META-INF/kinotic-persistence.properties")
public class KinoticPersistenceAutoConfiguration {

}
