package org.kinotic.domain_autoconfig;

import org.kinotic.core_autoconfig.KinoticCoreAutoConfiguration;
import org.kinotic.domain.KinoticDomainLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

// before = core: this module's bean definitions (ServiceDirectoryStrategy) must be registered before
// core's scan evaluates @ConditionalOnBean on DefaultServiceDirectory
@AutoConfiguration(before = KinoticCoreAutoConfiguration.class)
@Import(KinoticDomainLibrary.class)
public class KinoticDomainAutoConfiguration {}
