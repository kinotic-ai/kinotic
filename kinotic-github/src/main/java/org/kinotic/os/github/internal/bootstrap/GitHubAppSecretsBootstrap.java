package org.kinotic.os.github.internal.bootstrap;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretFileLoader;
import org.kinotic.core.api.secret.SecretStorageService;
import org.kinotic.os.github.api.config.KinoticGithubProperties;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

/**
 * On startup reads the GitHub App private key (PEM) and webhook secret from the
 * configured {@code secretsPath} mount and stores them via {@link SecretStorageService}
 * under {@code scope=kinotic-system}. Mirrors {@code PlatformOidcBootstrap} — the only
 * place secret material touches the filesystem.
 * <p>
 * Best-effort: a missing file is logged at warn and skipped so a partially-configured
 * deployment still boots; the App services then fail with a clear "not configured"
 * error when actually invoked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubAppSecretsBootstrap {

    public static final String SECRET_SCOPE = "kinotic-system";
    public static final String PRIVATE_KEY_KEY = "githubAppPrivateKey";
    public static final String WEBHOOK_SECRET_KEY = "githubWebhookSecret";

    private final KinoticGithubProperties properties;
    private final SecretStorageService secretStorageService;

    @PostConstruct
    public void start() {
        Path root = properties.getGithub().getSecretsPath();
        if (root == null) {
            log.info("kinotic.github.secretsPath unset; skipping GitHub App secret bootstrap");
            return;
        }
        loadAndStore(root.resolve(PRIVATE_KEY_KEY), PRIVATE_KEY_KEY);
        loadAndStore(root.resolve(WEBHOOK_SECRET_KEY), WEBHOOK_SECRET_KEY);
    }

    private void loadAndStore(Path file, String key) {
        String value = SecretFileLoader.read(file, key, log);
        if (value == null) {
            return;
        }
        secretStorageService.setSecret(SECRET_SCOPE, key, value).join();
        log.info("Loaded GitHub App secret '{}' from {}", key, file);
    }
}
