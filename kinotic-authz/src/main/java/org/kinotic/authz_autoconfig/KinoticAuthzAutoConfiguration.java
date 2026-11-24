package org.kinotic.authz_autoconfig;

import org.kinotic.authz.KinoticAuthzLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
@AutoConfiguration
@Import(KinoticAuthzLibrary.class)
public class KinoticAuthzAutoConfiguration {

}
