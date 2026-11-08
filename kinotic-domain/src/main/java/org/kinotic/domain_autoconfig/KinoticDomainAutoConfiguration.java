package org.kinotic.domain_autoconfig;

import org.kinotic.core_autoconfig.KinoticCoreAutoConfiguration;
import org.kinotic.domain.KinoticDomainLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

// before = core: this module's bean definitions (ServiceDirectoryStrategy) must be registered before
// core's scan evaluates @ConditionalOnBean on DefaultServiceDirectory
@AutoConfiguration(before = KinoticCoreAutoConfiguration.class)
@Import(KinoticDomainLibrary.class)
// the node attributes a node running this module advertises, merged into kinotic.ignite.nodeAttributes; the source
// joins the environment as this class is parsed, so a bean binds it but a condition evaluated earlier does not see it
@PropertySource("classpath:META-INF/kinotic-domain.properties")
public class KinoticDomainAutoConfiguration {
}
