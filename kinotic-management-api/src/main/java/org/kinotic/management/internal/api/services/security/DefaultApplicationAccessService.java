package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AccessCheckService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.authz.api.services.RolePermissionsService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.RoleOwner;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ScopedParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.security.RoleService;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
public class DefaultApplicationAccessService implements ApplicationAccessService {

    private static final String CUSTOM_ROLE_HOME = RoleService.APPLICATION_ROLE_HOME;

    private final SecurityContext securityContext;
    private final ApplicationRepository applications;
    private final ParticipantIdentityService identities;
    private final RelationshipService relationships;
    private final AccessCheckService accessChecks;
    private final RolePermissionsService rolePermissions;
    private final EntityDefinitionRepository entityDefinitions;
    private final TenantRepository tenants;
    private final AuthzModelService models;
    private final RoleService roleService;

    @Override
    public Future<Map<String, Set<String>>> findPermissions(String applicationId) {
        return requireApplication(applicationId).compose(this::model).map(model -> model.catalog(CUSTOM_ROLE_HOME));
    }

    @Override
    public Future<List<RoleDefinition>> findRoles(String applicationId) {
        return requireApplication(applicationId).compose(application -> model(application)
                .compose(model -> roleService.findRoles(RoleOwner.application(application), model)));
    }

    @Override
    public Future<RoleDefinition> saveRole(String applicationId, RoleDefinition role) {
        Validate.notNull(role, "role cannot be null");
        Validate.notBlank(role.name(), "role name cannot be blank");
        Validate.notNull(role.permissions(), "role permissions cannot be null");
        Validate.isTrue(role.resourceType() == null || CUSTOM_ROLE_HOME.equals(role.resourceType()),
                        "a role of an application is homed at %s, not %s", CUSTOM_ROLE_HOME, role.resourceType());
        // the model as far as the role goes: the permissions it names, and the type its id is homed at
        return requireApplication(applicationId).compose(application -> modelOf(application,
                                                                                role.id() == null ? List.of() : List.of(AuthzNameUtil.roleHome(role.id())),
                                                                                role.permissions()).compose(model -> {
            if (role.id() != null && model.roles().containsKey(role.id())) {
                throw new IllegalArgumentException("Role " + role.id() + " is built in and defined by the model");
            }
            RolePermissions permissions = bundled(role, model);
            RoleOwner owner = RoleOwner.application(application);
            return requireDefinitions(application, permissions.definitionPermissions().keySet())
                    .compose(v -> roleService.saveRole(owner, role, CUSTOM_ROLE_HOME,
                                                       saved -> rolePermissions.ensureRole(owner.store(), saved.getId(), permissions)))
                    .map(saved -> RoleService.definitionOf(saved, owner, model, permissions));
        }));
    }

    // What the role bundles, each permission one a tenant's role can hold: on everything, or, for a permission of
    // a definition's rows, on the definitions named. A permission bundled for everything names no definition
    private static RolePermissions bundled(RoleDefinition role, AuthzModel model) {
        Map<String, Set<String>> catalog = model.catalog(CUSTOM_ROLE_HOME);
        Set<String> bundleable = new HashSet<>();
        catalog.values().forEach(bundleable::addAll);
        for (String permission : role.permissions()) {
            if (!bundleable.contains(permission)) {
                throw new IllegalArgumentException("No permission a role of the application can bundle is named '" + permission + "'");
            }
        }
        RolePermissions held = model.bundle(role.permissions());
        Set<String> ofRows = catalog.getOrDefault(AuthzConstants.ENTITY_DEFINITION_TYPE, Set.of());
        Map<String, Set<String>> named = new TreeMap<>();
        Map<String, Set<String>> requested = role.definitionPermissions() == null ? Map.of() : role.definitionPermissions();
        for (Map.Entry<String, Set<String>> definition : requested.entrySet()) {
            Set<String> onIt = new TreeSet<>();
            for (String permission : definition.getValue()) {
                if (!ofRows.contains(permission)) {
                    throw new IllegalArgumentException("No permission of an entity definition's rows is named '" + permission + "'");
                }
                if (!held.permissions().contains(permission)) {
                    onIt.add(permission);
                }
            }
            if (!onIt.isEmpty()) {
                named.put(definition.getKey(), onIt);
            }
        }
        return new RolePermissions(held.permissions(), named, held.servicePermissions());
    }

    @Override
    public Future<Void> deleteRole(String applicationId, String roleId) {
        Validate.notBlank(roleId, "roleId cannot be blank");
        return requireApplication(applicationId)
                .compose(application -> models.findApplicationStoreModel(application.getOrganizationId(), application.getId())
                                              .compose(model -> roleService.deleteRole(RoleOwner.application(application), roleId, model)));
    }

    private Future<Void> requireDefinitions(Application application, Set<String> definitionIds) {
        Future<Void> ret = Future.succeededFuture();
        for (String definitionId : definitionIds) {
            ret = ret.compose(v -> requireDefinition(application, definitionId));
        }
        return ret;
    }

    @Override
    public Future<List<Grant>> findGrants(String applicationId, Resource resource) {
        validate(applicationId, resource);
        return requireApplication(applicationId).compose(application -> grantsOn(application, resource))
                                                .map(grants -> grants.stream().map(AuthzGraphUtil::localGrant).toList());
    }

    @Override
    public Future<Grant> grant(String applicationId, Subject subject, String roleId, Resource resource) {
        validate(applicationId, resource);
        Validate.notNull(subject, "subject cannot be null");
        if (subject.type() == SubjectType.APPLICATION) {
            Validate.isTrue(applicationId.equals(subject.id()), "a grant to every end user is made to those of the application %s, not %s", applicationId, subject.id());
        } else {
            validate(subject);
        }
        Validate.notBlank(roleId, "roleId cannot be blank");
        return requireApplication(applicationId)
                .compose(application -> requireSubject(application, subject)
                        .compose(v -> contained(application, resource))
                        .compose(v -> roleService.grant(application, subject, roleId, resource)));
    }

    @Override
    public Future<Void> revoke(String applicationId, Resource resource, String grantId) {
        validate(applicationId, resource);
        Validate.notBlank(grantId, "grantId cannot be blank");
        return requireApplication(applicationId).compose(application -> relationships.revoke(storeOf(application), grantId,
                                                                                             objectOf(application, resource)));
    }

    @Override
    public Future<AccessExplanation> explain(String applicationId, Subject subject, String permission, Resource resource) {
        validate(applicationId, resource);
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        return requireApplication(applicationId).compose(application -> {
            String store = storeOf(application);
            RelationshipTuple holds = new RelationshipTuple(AuthzGraphUtil.authzSubject(application.getOrganizationId(), subject), permission, objectOf(application, resource));
            return requireSubject(application, subject)
                    .compose(v -> Future.all(modelOf(application, List.of(), List.of(permission)), grantsOn(application, resource)))
                    .compose(both -> accessChecks.explain(store, both.resultAt(0), holds, edgesOf(resource), both.resultAt(1)))
                    .map(AuthzGraphUtil::localExplanation);
        });
    }

    // The grants made on the resource, then on each resource above it, as the store holds them
    private Future<List<Grant>> grantsOn(Application application, Resource resource) {
        String store = storeOf(application);
        Future<List<Grant>> ret = Future.succeededFuture(new ArrayList<>());
        for (Resource reached : reaching(application, resource)) {
            ret = ret.compose(all -> relationships.findGrants(store, objectOf(application, reached)).map(grants -> {
                all.addAll(grants);
                return all;
            }));
        }
        return ret;
    }

    // The resource and every resource a grant reaches it from: a definition within a tenant from the definition,
    // the tenant and the application, a tenant or a definition from the application
    private static List<Resource> reaching(Application application, Resource resource) {
        String definitionId = AuthzNameUtil.definitionOf(resource);
        String tenantId = AuthzNameUtil.tenantOf(resource);
        List<Resource> ret = new ArrayList<>();
        ret.add(resource);
        if (definitionId != null && tenantId != null) {
            ret.add(new Resource(AuthzConstants.ENTITY_DEFINITION_TYPE, definitionId));
            ret.add(new Resource(AuthzConstants.TENANT_TYPE, tenantId));
        }
        if (definitionId != null || tenantId != null) {
            ret.add(new Resource(AuthzConstants.APPLICATION_TYPE, application.getId()));
        }
        return ret;
    }

    // A grant is made on a tenant of the application, which is placed under it, on a definition the application
    // holds, or on both
    private Future<Void> contained(Application application, Resource resource) {
        String definitionId = AuthzNameUtil.definitionOf(resource);
        String tenantId = AuthzNameUtil.tenantOf(resource);
        Future<Void> ret = definitionId == null ? Future.succeededFuture() : requireDefinition(application, definitionId);
        if (tenantId != null) {
            ret = ret.compose(v -> placed(application, tenantId));
        }
        if (definitionId != null && tenantId != null) {
            // the store holds the edges of a definition within a tenant once it holds a grant on it, which is how
            // the deletion of either finds the grants made on the two together
            ret = ret.compose(v -> relationships.ensure(storeOf(application), edgesOf(resource)));
        }
        return ret;
    }

    // A tenant is placed under its application when it is first granted on, so the grant and every one made on
    // the application reach it; the application itself is where the graph starts
    private Future<Void> placed(Application application, String tenantId) {
        return requireTenant(application, tenantId)
                .compose(v -> relationships.ensure(storeOf(application),
                                                   List.of(AuthzGraphUtil.tenantContainment(application.getOrganizationId(), application.getId(), tenantId))));
    }

    // A tenant named by a grant is one the application has where its administrators manage its tenants; a tenant
    // the system gave a user has no record to require
    private Future<Void> requireTenant(Application application, String tenantId) {
        Future<Void> ret;
        if (application.getTenantManagement() == TenantManagement.TENANT_ADMINISTRATORS) {
            ret = tenants.findById(application.getId(), tenantId, application.getOrganizationId()).map(tenant -> {
                if (tenant == null) {
                    throw new IllegalArgumentException("No tenant of the application has id " + tenantId);
                }
                return null;
            });
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    // A grant on a definition is made on one the application holds, which placed it in the store when it was created
    private Future<Void> requireDefinition(Application application, String definitionId) {
        return entityDefinitions.findById(definitionId, application.getOrganizationId()).map(definition -> {
            if (definition == null || !application.getId().equals(definition.getApplicationId())) {
                throw new IllegalArgumentException("No entity definition of the application has id " + definitionId);
            }
            return null;
        });
    }

    // The edges reaching a definition within a tenant from the definition and from the tenant; none for any other resource
    private static List<RelationshipTuple> edgesOf(Resource resource) {
        String definitionId = AuthzNameUtil.definitionOf(resource);
        String tenantId = AuthzNameUtil.tenantOf(resource);
        return definitionId != null && tenantId != null ? AuthzGraphUtil.tenantDefinitionEdges(definitionId, tenantId) : List.of();
    }

    private Future<Application> requireApplication(String applicationId) {
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        return applications.findById(applicationId, organizationId)
                           .map(application -> DomainUtil.requireOwned(application, organizationId, "No application of the organization has id " + applicationId));
    }

    // The application's whole model: the system's services its users call and every service its runtimes registered
    private Future<AuthzModel> model(Application application) {
        return models.findApplicationModel(application.getOrganizationId(), application.getId());
    }

    // The application's model as far as the given types and permissions go
    private Future<AuthzModel> modelOf(Application application, Collection<String> types, Collection<String> permissions) {
        return models.findApplicationModelOf(application.getOrganizationId(), application.getId(), types, permissions);
    }

    // A grant in an application's store is made to one of its own users or machines, to the users of one of its
    // tenants, or to every end user it has
    private Future<Void> requireSubject(Application application, Subject subject) {
        Future<Void> ret;
        if (subject.type() == SubjectType.APPLICATION) {
            ret = Future.succeededFuture();
        } else if (subject.type() == SubjectType.TENANT) {
            ret = requireTenant(application, subject.id());
        } else {
            ret = identities.findById(subject.id()).map(identity -> {
                if (!belongs(identity, application)) {
                    throw new IllegalArgumentException("No user or machine of the application has id " + subject.id());
                }
                return null;
            });
        }
        return ret;
    }

    // a delegate carries its owner's scope but is withdrawn from no store when deleted, so it is never a subject
    private static boolean belongs(ParticipantIdentity identity, Application application) {
        return identity instanceof ScopedParticipantIdentity scoped
                && !(identity instanceof DelegatingParticipantIdentity)
                && application.getOrganizationId().equals(scoped.scope().organizationId())
                && application.getId().equals(scoped.scope().applicationId());
    }

    private static String storeOf(Application application) {
        return AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId());
    }

    private static String objectOf(Application application, Resource resource) {
        return AuthzGraphUtil.authzObject(application.getOrganizationId(), resource);
    }

    private static void validate(String applicationId, Resource resource) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notNull(resource, "resource cannot be null");
        Validate.notBlank(resource.id(), "resource id cannot be blank");
        boolean theApplication = AuthzConstants.APPLICATION_TYPE.equals(resource.type()) && applicationId.equals(resource.id());
        boolean pair = AuthzConstants.TENANT_DEFINITION_TYPE.equals(resource.type());
        Validate.isTrue(theApplication || AuthzConstants.TENANT_TYPE.equals(resource.type()) || AuthzConstants.ENTITY_DEFINITION_TYPE.equals(resource.type()) || pair,
                        "a grant in an application is made on the application itself, one of its tenants, one of its entity definitions"
                                + " or a definition within a tenant, not on %s", resource);
        Validate.isTrue(!pair || AuthzNameUtil.isTenantDefinitionId(resource.id()),
                        "a definition within a tenant is named <definition id>@<tenant id>, not %s", resource.id());
    }

    // An application's store has no groups, so a subject asked about is one of its identities or the users of one of
    // its tenants
    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.type() == SubjectType.USER || subject.type() == SubjectType.TENANT,
                        "a grant in an application is made to a user, a machine or the users of a tenant");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

}
