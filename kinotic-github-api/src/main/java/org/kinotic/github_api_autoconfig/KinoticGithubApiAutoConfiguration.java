package org.kinotic.github_api_autoconfig;

import org.kinotic.github.api.KinoticGithubApiLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(KinoticGithubApiLibrary.class)
public class KinoticGithubApiAutoConfiguration {
}
