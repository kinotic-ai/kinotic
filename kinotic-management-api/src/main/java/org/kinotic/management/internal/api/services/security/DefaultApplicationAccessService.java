package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ScopedParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
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
    private final RelationshipService relationships;
    private final EntityDefinitionRepository entityDefinitions;
    private final AuthzModelService models;

    @Override
    public Future<List<RoleDefinition>> findRoles(String applicationId) {
        return requireApplication(applicationId).compose(this::model).map(model -> model.builtInRoles(AuthzConstants.APPLICATION_TYPE));
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
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        return requireApplication(applicationId)
                .compose(application -> requireGrantable(application, roleId, resource.type())
                        .compose(v -> requireSubject(application, subject))
                        .compose(v -> contained(application, resource))
                        .compose(v -> relationships.bind(storeOf(application), roleId, AuthzGraphUtil.authzSubject(subject), objectOf(application, resource))))
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
            RelationshipTuple holds = new RelationshipTuple(AuthzGraphUtil.authzSubject(subject), permission, objectOf(application, resource));
            return requireSubject(application, subject)
                    .compose(v -> grantsOn(application, resource))
                    .compose(grants -> relationships.explain(store, subject, holds, edgesOf(resource), grants))
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
        List<Resource> ret = new ArrayList<>();
        ret.add(resource);
        if (AuthzConstants.TENANT_DEFINITION_TYPE.equals(resource.type())) {
            ret.add(new Resource(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzUtil.definitionOf(resource.id())));
            ret.add(new Resource(AuthzConstants.TENANT_TYPE, AuthzUtil.tenantOf(resource.id())));
        }
        if (!AuthzConstants.APPLICATION_TYPE.equals(resource.type())) {
            ret.add(new Resource(AuthzConstants.APPLICATION_TYPE, application.getId()));
        }
        return ret;
    }

    // A grant is made on a tenant placed under the application, on a definition the application holds, or on both
    private Future<Void> contained(Application application, Resource resource) {
        Future<Void> ret;
        if (AuthzConstants.TENANT_TYPE.equals(resource.type())) {
            ret = placed(application, resource.id());
        } else if (AuthzConstants.ENTITY_DEFINITION_TYPE.equals(resource.type())) {
            ret = requireDefinition(application, resource.id());
        } else if (AuthzConstants.TENANT_DEFINITION_TYPE.equals(resource.type())) {
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
        return relationships.ensure(storeOf(application),
                                    List.of(AuthzGraphUtil.containment(AuthzGraphUtil.authzApplication(application.getOrganizationId(), application.getId()),
                                                                       AuthzUtil.object(AuthzConstants.TENANT_TYPE, tenantId))));
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
        return AuthzConstants.TENANT_DEFINITION_TYPE.equals(resource.type())
                ? AuthzGraphUtil.tenantDefinitionEdges(AuthzUtil.definitionOf(resource.id()), AuthzUtil.tenantOf(resource.id()))
                : List.of();
    }

    private Future<Application> requireApplication(String applicationId) {
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        return applications.findById(applicationId, organizationId)
                           .map(application -> DomainUtil.requireOwned(application, organizationId, "No application of the organization has id " + applicationId));
    }

    // A role of the application's model homed at the resource's type or inside it
    private Future<Void> requireGrantable(Application application, String roleId, String resourceType) {
        return model(application).map(model -> {
            if (!model.roles().containsKey(roleId)) {
                throw new IllegalArgumentException("No role of the application has id " + roleId);
            }
            if (!model.grantable(roleId, resourceType)) {
                throw new IllegalArgumentException("Role " + roleId + " is homed at " + AuthzUtil.roleHome(roleId)
                                                           + " and cannot be granted on a " + resourceType);
            }
            return null;
        });
    }

    // The model the application's store runs: the system's services its users call and the services its
    // runtimes registered
    private Future<AuthzModel> model(Application application) {
        return models.findApplicationModel(application.getOrganizationId(), application.getId());
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
        return identity instanceof ScopedParticipantIdentity scoped
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
        Validate.isTrue(!pair || resource.id().indexOf('@') > 0,
                        "a definition within a tenant is named <definition id>@<tenant id>, not %s", resource.id());
    }

    // An application's store has no groups, so a subject is one of its identities
    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.type() == SubjectType.USER, "a grant in an application is made to a user or a machine");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

}
