package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.PermissionNamespace;
import org.kinotic.idl.api.annotations.RequirePermission;
import org.kinotic.idl.api.annotations.ResourceTarget;

@PermissionNamespace("projects")
@ResourceTarget(type = "project")
@RequirePermission
public interface AuthorizationTestService {
    @RequirePermission("repo.initialize")
    @ResourceTarget(idArgument = "projectId")
    String initialize(String reason, String projectId);
    String list(String searchText);
}
