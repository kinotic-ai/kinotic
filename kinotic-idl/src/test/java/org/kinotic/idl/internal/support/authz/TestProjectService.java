package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A resource service whose checks are derived from its functions' shapes, with two stated by {@link AuthzCheck}.
 */
@AuthzResource(value = "project", parent = "application")
public interface TestProjectService {

    CompletableFuture<TestProject> findById(String id);

    CompletableFuture<List<TestProject>> findAllForApplication(String applicationId, Integer limit);

    CompletableFuture<TestProject> save(TestProject value);

    CompletableFuture<TestProject> createProjectIfNotExist(TestProject project);

    @AuthzCheck(permission = "can_edit")
    CompletableFuture<Void> retryRepoInitialization(String projectId);

    @AuthzCheck(permission = "can_deploy", implies = "can_view")
    CompletableFuture<Void> deploy(TestCallerContext context, String projectId);

    CompletableFuture<Long> count();

}
