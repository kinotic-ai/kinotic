package org.kinotic.authz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-authz module: the relationships the system's authorization engine
 * holds, and the checks it answers from them.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
public class KinoticAuthzLibrary {
}
