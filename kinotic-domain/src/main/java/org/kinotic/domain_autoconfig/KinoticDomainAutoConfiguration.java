package org.kinotic.domain_autoconfig;

import org.kinotic.domain.KinoticDomainLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(KinoticDomainLibrary.class)
public class KinoticDomainAutoConfiguration {}
