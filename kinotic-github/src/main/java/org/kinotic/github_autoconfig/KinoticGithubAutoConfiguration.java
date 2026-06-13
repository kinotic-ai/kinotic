package org.kinotic.github_autoconfig;

import org.kinotic.github.KinoticGithubLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(KinoticGithubLibrary.class)
public class KinoticGithubAutoConfiguration {
}
