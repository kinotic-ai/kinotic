package org.kinotic.os.api.services;

import org.kinotic.core.api.crud.CrudService;
import org.kinotic.domain.api.model.Application;
import org.kinotic.idl.api.annotations.McpTool;

/**
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
@McpTool
public interface TestCrudSweptService extends CrudService<Application, String> {
}
