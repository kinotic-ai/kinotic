package org.kinotic.client_autoconfig;

import org.kinotic.client.KinoticClientLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
@AutoConfiguration
@Import(KinoticClientLibrary.class)
public class KinoticClientAutoConfiguration {

}
