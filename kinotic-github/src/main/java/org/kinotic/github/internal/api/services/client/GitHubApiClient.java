package org.kinotic.github.internal.api.services.client;

import io.vertx.core.Future;
import org.kinotic.github.api.model.GitHubToken;

import java.util.Map;

/**
 *
 * Created By Navíd Mitchell 🤪on 5/4/26
 */
public interface GitHubApiClient {
    Future<Void> createRef(String installationToken,
                           String repoFullName,
                           String refName,
                           String sha);

    Future<CreatedRepository> createRepoFromTemplate(String installationToken,
                                                     String templateFullName,
                                                     String owner,
                                                     String name,
                                                     String description,
                                                     boolean isPrivate);

    Future<InstallationDetails> getInstallation(long installationId);

    Future<GitHubToken> getToken(long installationId,
                                 Long repoId,
                                 Map<String, String> permissions);
}
