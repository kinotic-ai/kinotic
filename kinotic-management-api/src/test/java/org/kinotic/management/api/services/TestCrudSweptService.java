package org.kinotic.management.api.services;

import org.kinotic.core.api.crud.CrudService;
import org.kinotic.domain.api.model.Application;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.utils.AuthzUtil;

/**
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@McpTool
@AuthzResource(value = AuthzUtil.APPLICATION_TYPE, parent = AuthzUtil.ORGANIZATION_TYPE)
public interface TestCrudSweptService extends CrudService<Application, String> {
}
