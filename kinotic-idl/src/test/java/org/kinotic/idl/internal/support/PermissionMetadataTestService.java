package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.PermissionNamespace;
import org.kinotic.idl.api.annotations.RequirePermission;
import org.kinotic.idl.api.annotations.ResourceTarget;

@PermissionNamespace("metadata")
@RequirePermission("inspect")
@ResourceTarget(type = "project", idArgument = "resourceId")
public interface PermissionMetadataTestService {
    Class<?> inspect(String requestId, String resourceId);
}
