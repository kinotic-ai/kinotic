package org.kinotic.github.api;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-github-api module.
 */
@Configuration
@EnableKinotic // registers org.kinotic.github.api so @Proxy interfaces like GitHubProjectEventService are scanned
public class KinoticGithubApiLibrary {
}
