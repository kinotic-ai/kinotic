package org.kinotic.github.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.RepositoryConnectionStatus;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubToken;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.services.client.CreatedRepository;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.GitHubApiException;
import org.kinotic.github.internal.api.services.client.TreeEntry;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubProjectRepoProvisionerTest {

    private GitHubAppInstallationService installationService;
    private GitHubApiClient apiClient;
    private GitHubProjectRepoProvisioner provisioner;

    @BeforeEach
    void setUp() {
        installationService = mock(GitHubAppInstallationService.class);
        apiClient = mock(GitHubApiClient.class);
        KinoticGithubProperties properties = new KinoticGithubProperties();
        properties.getGithub().setRepoTemplate("kinotic-ai/project-template");
        provisioner = new GitHubProjectRepoProvisioner(installationService, apiClient,
                                                       properties, new GraalJsSpawnRenderer());

        GitHubAppInstallation install = new GitHubAppInstallation();
        install.setGithubInstallationId(42L);
        install.setAccountLogin("acme");
        when(installationService.findForCurrentOrg())
                .thenReturn(CompletableFuture.completedFuture(install));
        when(apiClient.getToken(eq(42L), isNull(), eq(GitHubApiClient.WRITE_CONTENTS)))
                .thenReturn(Future.succeededFuture(new GitHubToken("install-token", Instant.now().plusSeconds(3600))));
        when(apiClient.getToken(eq(42L), eq(99L), eq(GitHubApiClient.WRITE_CONTENTS)))
                .thenReturn(Future.succeededFuture(new GitHubToken("repo-token", Instant.now().plusSeconds(3600))));
        when(apiClient.createRepoFromTemplate(eq("install-token"), eq("kinotic-ai/project-template"),
                                              eq("acme"), eq("demo"), any(), eq(true)))
                .thenReturn(Future.succeededFuture(new CreatedRepository(99L, "acme/demo", "main")));
        when(apiClient.createBlob(eq("repo-token"), eq("acme/demo"), any()))
                .thenReturn(Future.succeededFuture("blob-sha"));
        when(apiClient.createTree(eq("repo-token"), eq("acme/demo"), anyList()))
                .thenReturn(Future.succeededFuture("tree-sha"));
        when(apiClient.createCommit(eq("repo-token"), eq("acme/demo"), anyString(), eq("tree-sha")))
                .thenReturn(Future.succeededFuture("commit-sha"));
        when(apiClient.updateRef(eq("repo-token"), eq("acme/demo"), eq("heads/main"), eq("commit-sha"), eq(true)))
                .thenReturn(Future.succeededFuture());
    }

    @Test
    void rendersTheTemplateIntoASingleRootCommit() throws Exception {
        when(apiClient.downloadTarball(eq("repo-token"), eq("acme/demo"), eq("main")))
                .thenReturn(Future.succeededFuture(templateTarball()));

        Project project = provisioner.provision(project()).get();

        assertEquals("acme/demo", project.getRepoFullName());
        assertEquals(99L, project.getRepoId());
        assertEquals("main", project.getDefaultBranch());
        assertEquals(RepositoryConnectionStatus.CONNECTED, project.getRepositoryConnectionStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TreeEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiClient).createTree(eq("repo-token"), eq("acme/demo"), captor.capture());
        Map<String, TreeEntry> tree = captor.getValue().stream()
                                            .collect(java.util.stream.Collectors.toMap(TreeEntry::path, e -> e));

        // .liquid content rendered with the project context, suffix stripped
        assertEquals("{\"name\": \"demo\", \"org\": \"org-1\", \"app\": \"app-1\"}",
                     tree.get("package.json").content());
        // liquid in paths rendered
        assertTrue(tree.containsKey("src/Demo.ts"));
        // spawn.json consumed by the engine, not committed
        assertTrue(!tree.containsKey("spawn.json"));
        // executable bit survives for untemplated text files
        assertEquals(TreeEntry.MODE_EXECUTABLE, tree.get("gradlew").mode());
        // ...and for templated paths, traced back through the render source map
        assertEquals("#!/bin/sh\necho demo\n", tree.get("bin/demo.sh").content());
        assertEquals(TreeEntry.MODE_EXECUTABLE, tree.get("bin/demo.sh").mode());
        // binary files ride as blobs created out of band
        assertEquals("blob-sha", tree.get("assets/logo.png").sha());

        verify(apiClient).createCommit(eq("repo-token"), eq("acme/demo"), anyString(), eq("tree-sha"));
        verify(apiClient).updateRef(eq("repo-token"), eq("acme/demo"), eq("heads/main"), eq("commit-sha"), eq(true));
    }

    @Test
    void retriesTheTarballWhileTheTemplateCopyIsPending() throws Exception {
        when(apiClient.downloadTarball(eq("repo-token"), eq("acme/demo"), eq("main")))
                .thenReturn(Future.failedFuture(new GitHubApiException("downloadTarball failed: HTTP 404")))
                .thenReturn(Future.succeededFuture(templateTarball()));

        Project project = provisioner.provision(project()).get();

        assertEquals(RepositoryConnectionStatus.CONNECTED, project.getRepositoryConnectionStatus());
        verify(apiClient, times(2)).downloadTarball(eq("repo-token"), eq("acme/demo"), eq("main"));
    }

    @Test
    void adoptsTheRepoWhenInitializationFails() throws Exception {
        when(apiClient.downloadTarball(eq("repo-token"), eq("acme/demo"), eq("main")))
                .thenReturn(Future.succeededFuture(templateTarball()));
        when(apiClient.updateRef(eq("repo-token"), eq("acme/demo"), eq("heads/main"), eq("commit-sha"), eq(true)))
                .thenReturn(Future.failedFuture(new GitHubApiException("updateRef failed: HTTP 500")));

        // provision succeeds even though initialization failed: the repo was created,
        // so the project is adopted with a retryable status rather than orphaned.
        Project project = provisioner.provision(project()).get();

        assertEquals("acme/demo", project.getRepoFullName());
        assertEquals(99L, project.getRepoId());
        assertEquals(RepositoryConnectionStatus.INITIALIZATION_FAILED, project.getRepositoryConnectionStatus());
    }

    @Test
    void reinitializeRendersWithoutCreatingANewRepo() throws Exception {
        when(apiClient.downloadTarball(eq("repo-token"), eq("acme/demo"), eq("main")))
                .thenReturn(Future.succeededFuture(templateTarball()));

        Project failed = project();
        failed.setRepoFullName("acme/demo");
        failed.setRepoId(99L);
        failed.setDefaultBranch("main");
        failed.setRepositoryConnectionStatus(RepositoryConnectionStatus.INITIALIZATION_FAILED);

        Project project = provisioner.reinitialize(failed).get();

        assertEquals(RepositoryConnectionStatus.CONNECTED, project.getRepositoryConnectionStatus());
        verify(apiClient, never()).createRepoFromTemplate(anyString(), anyString(), anyString(),
                                                          anyString(), any(), anyBoolean());
        verify(apiClient).updateRef(eq("repo-token"), eq("acme/demo"), eq("heads/main"), eq("commit-sha"), eq(true));
    }

    private static Project project() {
        Project project = new Project();
        project.setName("demo");
        project.setOrganizationId("org-1");
        project.setApplicationId("app-1");
        project.setRepoPrivate(true);
        return project;
    }

    private static Buffer templateTarball() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
            addEntry(tar, "root/spawn.json",
                     "{\"propertySchema\": {\"projectName\": {\"type\": \"string\"}}}".getBytes(StandardCharsets.UTF_8), false);
            addEntry(tar, "root/package.json.liquid",
                     "{\"name\": \"{{projectName}}\", \"org\": \"{{organization}}\", \"app\": \"{{application}}\"}"
                             .getBytes(StandardCharsets.UTF_8), false);
            addEntry(tar, "root/src/{{ projectName | camelCase | upperFirst }}.ts.liquid",
                     "export class {{ projectName | camelCase | upperFirst }} {}".getBytes(StandardCharsets.UTF_8), false);
            addEntry(tar, "root/gradlew", "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8), true);
            addEntry(tar, "root/bin/{{ projectName }}.sh.liquid",
                     "#!/bin/sh\necho {{ projectName }}\n".getBytes(StandardCharsets.UTF_8), true);
            addEntry(tar, "root/assets/logo.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1}, false);
        }
        return Buffer.buffer(out.toByteArray());
    }

    private static void addEntry(TarArchiveOutputStream tar, String name, byte[] content, boolean executable)
            throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        entry.setMode(executable ? 0100755 : 0100644);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

}
