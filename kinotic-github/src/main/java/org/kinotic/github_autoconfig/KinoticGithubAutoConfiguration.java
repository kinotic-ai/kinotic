package org.kinotic.github_autoconfig;

import org.kinotic.github.KinoticGithubLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration entry point for kinotic-github.
 * Lives in a dedicated package outside {@code org.kinotic.os.github} to avoid being
 * picked up by the library's own component scan (which would double-register the
 * imported {@link KinoticGithubLibrary} configuration).
 */
@AutoConfiguration
@Import(KinoticGithubLibrary.class)
public class KinoticGithubAutoConfiguration {
}
