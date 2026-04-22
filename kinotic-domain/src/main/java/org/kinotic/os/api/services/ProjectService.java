package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.ApplicationScopedCrudService;
import org.kinotic.os.api.model.Project;

import java.util.concurrent.CompletableFuture;

@Publish
public interface ProjectService extends ApplicationScopedCrudService<Project, String> {

    CompletableFuture<Project> createProjectIfNotExist(Project project);

}
