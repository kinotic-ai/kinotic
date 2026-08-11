package org.kinotic.billing_autoconfig;

import org.kinotic.billing.KinoticBillingLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(KinoticBillingLibrary.class)
public class KinoticBillingAutoConfiguration {
}
