package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzUnchecked;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A service acting on the caller's own organization: the service names the object its functions are checked
 * on, whatever their arguments carry; one function names its own object, one is unchecked and one asks for a
 * consistent answer.
 */
@AuthzResource(value = "organization", resourceId = "{@organizationId}")
public interface TestMemberService {

    @AuthzCheck(permission = "can_view_members")
    CompletableFuture<List<TestProject>> findMembers(Integer limit);

    @AuthzCheck(permission = "can_manage_members")
    CompletableFuture<TestProject> saveRole(TestProject role);

    @AuthzCheck(permission = "can_manage_members")
    CompletableFuture<Void> createInvite(String email);

    @AuthzCheck(permission = "can_manage_members", consistent = true)
    CompletableFuture<Void> removeMember(String memberId);

    @AuthzCheck(permission = "can_view", resourceId = "{projectId}", resource = "project")
    CompletableFuture<TestProject> findProject(String projectId);

    @AuthzUnchecked
    CompletableFuture<List<String>> listAccessible(String type, String permission);

}
