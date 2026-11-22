package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.api.model.security.RoleOwner;
import org.kinotic.domain.api.repositories.RoleRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.RoleService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.CursorPaging;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DefaultRoleService implements RoleService {

    // an owner's roles are read whole, a page at a time
    private static final int ROLE_PAGE_SIZE = 1000;

    private final RoleRepository roles;
    private final RelationshipService relationships;
    private final AuthzModelService models;

    @Override
    public Future<List<RoleDefinition>> findRoles(RoleOwner owner, AuthzModel model) {
        // in name order, with the id ordering roles that share a name so the paging has a unique order
        return CursorPaging.readAll(pageable -> owner.applicationId() == null
                                            ? roles.findAllOfOrganization(owner.organizationId(), pageable)
                                            : roles.findAllForApplication(owner.applicationId(), owner.organizationId(), pageable),
                                    ROLE_PAGE_SIZE, Sort.by("name", "id"))
                           .compose(rows -> Future.all(rows.stream()
                                                           .map(row -> relationships.findRolePermissions(owner.store(), row.getId())
                                                                                    .map(permissions -> RoleService.definitionOf(row, owner, model, permissions)))
                                                           .toList()))
                           .map(own -> {
                               List<RoleDefinition> ret = new ArrayList<>(model.builtInRoles(owner.type()));
                               ret.addAll(own.list());
                               return ret;
                           });
    }

    @Override
    public Future<Role> requireRole(RoleOwner owner, String roleId) {
        return roles.findById(roleId, owner.organizationId()).map(role -> {
            if (role == null || !Objects.equals(owner.applicationId(), role.getApplicationId())) {
                throw new IllegalArgumentException("No role of the " + owner.type() + " has id " + roleId);
            }
            return role;
        });
    }

    @Override
    public Future<Role> saveRole(RoleOwner owner, RoleDefinition role, String home, Function<Role, Future<Void>> bundle) {
        String roleId = role.id() == null ? AuthzNameUtil.roleId(home, UUID.randomUUID().toString()) : role.id();
        // under the lock a grant and a deletion of the role take, with the role read under it, so an edit that
        // waited for a deletion finds no role and writes none
        return relationships.exclusively(AuthzNameUtil.roleObject(roleId), () -> {
            Future<Role> row = role.id() == null
                    ? Future.succeededFuture(new Role().setId(roleId)
                                                       .setOrganizationId(owner.organizationId())
                                                       .setApplicationId(owner.applicationId())
                                                       .setCreated(new Date()))
                    : requireRole(owner, roleId);
            return row.compose(existing -> roles.saveSync(existing.setName(role.name())
                                                                  .setDescription(role.description())
                                                                  // only an application has tenants whose administrators grant
                                                                  .setTenantAssignable(owner.applicationId() != null && role.tenantAssignable())
                                                                  .setUpdated(new Date()),
                                                          owner.organizationId()))
                      .compose(saved -> bundle.apply(saved).map(saved));
        });
    }

    @Override
    public Future<Void> deleteRole(RoleOwner owner, String roleId, AuthzModel model) {
        String role = AuthzNameUtil.roleObject(roleId);
        // under the lock a grant of the role takes, so every grant of it is made before the grants are read here or
        // finds the role gone
        return relationships.exclusively(role, () -> requireRole(owner, roleId)
                .compose(row -> relationships.findBindings(owner.store(), role, model.grantableOn(AuthzNameUtil.roleHome(roleId), owner.type())))
                .compose(bindings -> {
                    // a grant is revoked where it was made, so a role a grant still holds stays
                    if (!bindings.isEmpty()) {
                        throw new IllegalStateException("Role " + roleId + " is still held by grants; revoke them first");
                    }
                    return relationships.removeObject(owner.store(), role);
                })
                .compose(v -> roles.deleteByIdSync(roleId, owner.organizationId())));
    }

    @Override
    public <T> Future<T> whileRoleKept(RoleOwner owner, AuthzModel model, String roleId, Supplier<Future<T>> grant) {
        return model.roles().containsKey(roleId)
                ? grant.get()
                : relationships.exclusively(AuthzNameUtil.roleObject(roleId), () -> requireRole(owner, roleId).compose(row -> grant.get()));
    }

    @Override
    public Future<Grant> grant(Application application, Subject subject, String roleId, Resource resource) {
        RoleOwner owner = RoleOwner.application(application);
        String member = AuthzGraphUtil.authzSubject(application.getOrganizationId(), subject);
        String object = AuthzGraphUtil.authzObject(application.getOrganizationId(), resource);
        return models.findApplicationModelOfRole(application.getOrganizationId(), application.getId(), roleId).compose(model -> {
            model.requireGrantable(roleId, resource.type());
            return whileRoleKept(owner, model, roleId, () -> relationships.bind(owner.store(), roleId, member, object));
        }).map(grantId -> new Grant(grantId, roleId, subject, resource));
    }
}
