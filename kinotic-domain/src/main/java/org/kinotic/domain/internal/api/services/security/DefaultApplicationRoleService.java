package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.api.repositories.RoleRepository;
import org.kinotic.domain.api.services.security.ApplicationRoleService;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.CursorPaging;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultApplicationRoleService implements ApplicationRoleService {

    // an application's roles are read whole, a page at a time
    private static final int ROLE_PAGE_SIZE = 1000;

    private final RoleRepository roles;
    private final RelationshipService relationships;
    private final AuthzModelService models;

    @Override
    public Future<List<RoleDefinition>> findRoles(Application application) {
        String store = storeOf(application);
        // a role of the application's own is homed at the tenant, which the store places
        return models.findApplicationStoreModel(application.getOrganizationId(), application.getId())
                     // in name order, with the id ordering roles that share a name so the paging has a unique order
                     .compose(model -> CursorPaging.readAll(pageable -> roles.findAllForApplication(application.getId(), application.getOrganizationId(), pageable),
                                                            ROLE_PAGE_SIZE, Sort.by("name", "id"))
                                                   .compose(rows -> Future.all(rows.stream()
                                                                                   .map(row -> relationships.findRolePermissions(store, row.getId())
                                                                                                            .map(permissions -> ApplicationRoleService.definitionOf(row, model, permissions)))
                                                                                   .toList())))
                     .map(defined -> defined.list());
    }

    @Override
    public Future<Role> requireRole(Application application, String roleId) {
        return roles.findById(roleId, application.getOrganizationId()).map(role -> {
            if (role == null || !application.getId().equals(role.getApplicationId())) {
                throw new IllegalArgumentException("No role of the application has id " + roleId);
            }
            return role;
        });
    }

    @Override
    public Future<Grant> grant(Application application, Subject subject, String roleId, Resource resource) {
        String store = storeOf(application);
        String member = AuthzGraphUtil.authzSubject(application.getOrganizationId(), subject);
        String object = AuthzGraphUtil.authzObject(application.getOrganizationId(), resource);
        return models.findApplicationModelOfRole(application.getOrganizationId(), application.getId(), roleId).compose(model -> {
            model.requireGrantable(roleId, resource.type());
            // a role of the model never changes; one of the application's own is read once the lock a change or a
            // deletion of it takes is held
            return model.roles().containsKey(roleId)
                    ? relationships.bind(store, roleId, member, object)
                    : relationships.exclusively(AuthzNameUtil.roleObject(roleId),
                                                () -> requireRole(application, roleId).compose(row -> relationships.bind(store, roleId, member, object)));
        }).map(grantId -> new Grant(grantId, roleId, subject, resource));
    }

    private static String storeOf(Application application) {
        return AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId());
    }
}
