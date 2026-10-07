package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultApplicationAccessService implements ApplicationAccessService {

    private final SecurityContext securityContext;
    private final ApplicationRepository applications;
    private final ParticipantIdentityService identities;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;
    private final EntityDefinitionRepository entityDefinitions;

    @Override
    public Future<List<RoleDefinition>> findRoles(String applicationId) {
        return requireApplication(applicationId).compose(application -> relationships.findRoles(storeOf(application)));
    }

    @Override
    public Future<List<Grant>> findGrants(String applicationId, Resource resource) {
        validate(applicationId, resource);
        return requireApplication(applicationId).compose(application -> grantsOn(application, resource))
                                                .map(grants -> grants.stream().map(DomainUtil::localGrant).toList());
    }

    @Override
    public Future<Grant> grant(String applicationId, Subject subject, String roleId, Resource resource) {
        validate(applicationId, resource);
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        return requireApplication(applicationId)
                .compose(application -> requireRole(application, roleId)
                        .compose(v -> requireSubject(application, subject))
                        .compose(v -> contained(application, resource))
                        .compose(v -> relationships.bind(storeOf(application), roleId, userOf(subject), objectOf(application, resource))))
                .map(bindingId -> new Grant(bindingId, roleId, subject, resource));
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
            RelationshipTuple holds = new RelationshipTuple(userOf(subject), permission, objectOf(application, resource));
            return requireSubject(application, subject)
                    .compose(v -> grantsOn(application, resource))
                    .compose(grants -> stores.modelId(store).compose(modelId -> {
                        // an administrator asks after changing access, so the answer must not predate the change
                        Future<Boolean> allowed = relationships.check(store, modelId, holds, Consistency.HIGHER_CONSISTENCY, edgesOf(resource));
                        List<Future<Boolean>> explains = grants.stream()
                                                               .map(grant -> relationships.explains(store, modelId, grant, subject, permission))
                                                               .toList();
                        return Future.all(explains).compose(results -> allowed.map(held -> {
                            List<Grant> through = new ArrayList<>();
                            for (int i = 0; i < grants.size(); i++) {
                                if (results.<Boolean>resultAt(i)) {
                                    through.add(DomainUtil.localGrant(grants.get(i)));
                                }
                            }
                            return new AccessExplanation(held, through);
                        }));
                    }));
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
        List<Resource> ret = new ArrayList<>();
        ret.add(resource);
        if (AuthzUtil.TENANT_DEFINITION_TYPE.equals(resource.type())) {
            ret.add(new Resource(AuthzUtil.ENTITY_DEFINITION_TYPE, AuthzUtil.definitionOf(resource.id())));
            ret.add(new Resource(AuthzUtil.TENANT_TYPE, AuthzUtil.tenantOf(resource.id())));
        }
        if (!AuthzUtil.APPLICATION_TYPE.equals(resource.type())) {
            ret.add(new Resource(AuthzUtil.APPLICATION_TYPE, application.getId()));
        }
        return ret;
    }

    // A grant is made on a tenant placed under the application, on a definition the application holds, or on both
    private Future<Void> contained(Application application, Resource resource) {
        Future<Void> ret;
        if (AuthzUtil.TENANT_TYPE.equals(resource.type())) {
            ret = placed(application, resource.id());
        } else if (AuthzUtil.ENTITY_DEFINITION_TYPE.equals(resource.type())) {
            ret = requireDefinition(application, resource.id());
        } else if (AuthzUtil.TENANT_DEFINITION_TYPE.equals(resource.type())) {
            ret = requireDefinition(application, AuthzUtil.definitionOf(resource.id()))
                    .compose(v -> placed(application, AuthzUtil.tenantOf(resource.id())));
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    // A tenant is placed under its application when it is first granted on, so the grant and every one made on
    // the application reach it; the application itself is where the graph starts
    private Future<Void> placed(Application application, String tenantId) {
        return relationships.ensure(storeOf(application), List.of(new RelationshipTuple(applicationObjectOf(application),
                                                                                        AuthzUtil.APPLICATION_TYPE,
                                                                                        AuthzUtil.object(AuthzUtil.TENANT_TYPE, tenantId))));
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

    private static List<RelationshipTuple> edgesOf(Resource resource) {
        return AuthzUtil.TENANT_DEFINITION_TYPE.equals(resource.type())
                ? DomainUtil.tenantDefinitionEdges(AuthzUtil.definitionOf(resource.id()), AuthzUtil.tenantOf(resource.id()))
                : List.of();
    }

    private Future<Application> requireApplication(String applicationId) {
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        return applications.findById(applicationId, organizationId)
                           .map(application -> DomainUtil.requireOwned(application, organizationId, "No application of the organization has id " + applicationId));
    }

    private Future<Void> requireRole(Application application, String roleId) {
        return relationships.read(storeOf(application), AuthzUtil.object(AuthzUtil.ROLE_TYPE, roleId)).map(held -> {
            if (held.isEmpty()) {
                throw new IllegalArgumentException("No role of the application has id " + roleId);
            }
            return null;
        });
    }

    // A grant in an application's store is made to one of its own users or machines
    private Future<Void> requireSubject(Application application, Subject subject) {
        return identities.findById(subject.id()).map(identity -> {
            if (!belongs(identity, application)) {
                throw new IllegalArgumentException("No user or machine of the application has id " + subject.id());
            }
            return null;
        });
    }

    private static boolean belongs(ParticipantIdentity identity, Application application) {
        return identity != null
                && application.getOrganizationId().equals(identity.getOrganizationId())
                && application.getId().equals(identity.getApplicationId());
    }

    private static String storeOf(Application application) {
        return DomainUtil.authzApplicationId(application.getOrganizationId(), application.getId());
    }

    private static String applicationObjectOf(Application application) {
        return AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, DomainUtil.authzApplicationId(application.getOrganizationId(), application.getId()));
    }

    private static String objectOf(Application application, Resource resource) {
        return DomainUtil.authzObject(application.getOrganizationId(), resource);
    }

    private static void validate(String applicationId, Resource resource) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notNull(resource, "resource cannot be null");
        Validate.notBlank(resource.id(), "resource id cannot be blank");
        boolean theApplication = AuthzUtil.APPLICATION_TYPE.equals(resource.type()) && applicationId.equals(resource.id());
        boolean pair = AuthzUtil.TENANT_DEFINITION_TYPE.equals(resource.type());
        Validate.isTrue(theApplication || AuthzUtil.TENANT_TYPE.equals(resource.type()) || AuthzUtil.ENTITY_DEFINITION_TYPE.equals(resource.type()) || pair,
                        "a grant in an application is made on the application itself, one of its tenants, one of its entity definitions"
                                + " or a definition within a tenant, not on %s", resource);
        Validate.isTrue(!pair || resource.id().indexOf('@') > 0,
                        "a definition within a tenant is named <definition id>@<tenant id>, not %s", resource.id());
    }

    // An application's store has no groups, so a subject is one of its identities
    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.kind() == SubjectKind.USER, "a grant in an application is made to a user or a machine");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

    private static String userOf(Subject subject) {
        return AuthzUtil.object(AuthzUtil.USER_TYPE, subject.id());
    }
}
