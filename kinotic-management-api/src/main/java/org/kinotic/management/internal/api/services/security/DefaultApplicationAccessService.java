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

    @Override
    public Future<List<RoleDefinition>> findRoles(String applicationId) {
        return requireApplication(applicationId).compose(application -> relationships.findRoles(applicationId));
    }

    @Override
    public Future<List<Grant>> findGrants(String applicationId, Resource resource) {
        validate(applicationId, resource);
        return requireApplication(applicationId).compose(application -> grantsOn(applicationId, resource));
    }

    @Override
    public Future<Grant> grant(String applicationId, Subject subject, String roleId, Resource resource) {
        validate(applicationId, resource);
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        return requireApplication(applicationId)
                .compose(application -> requireRole(applicationId, roleId))
                .compose(v -> requireSubject(applicationId, subject))
                .compose(v -> contained(applicationId, resource))
                .compose(v -> relationships.bind(applicationId, roleId, userOf(subject), objectOf(resource)))
                .map(bindingId -> new Grant(bindingId, roleId, subject, resource));
    }

    @Override
    public Future<Void> revoke(String applicationId, Resource resource, String grantId) {
        validate(applicationId, resource);
        Validate.notBlank(grantId, "grantId cannot be blank");
        return requireApplication(applicationId).compose(application -> relationships.revoke(applicationId, grantId, objectOf(resource)));
    }

    @Override
    public Future<AccessExplanation> explain(String applicationId, Subject subject, String permission, Resource resource) {
        validate(applicationId, resource);
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        RelationshipTuple holds = new RelationshipTuple(userOf(subject), permission, objectOf(resource));
        return requireApplication(applicationId)
                .compose(application -> requireSubject(applicationId, subject))
                .compose(v -> grantsOn(applicationId, resource))
                .compose(grants -> stores.modelId(applicationId).compose(modelId -> {
                    // an administrator asks after changing access, so the answer must not predate the change
                    Future<Boolean> allowed = relationships.check(applicationId, modelId, holds, Consistency.HIGHER_CONSISTENCY);
                    List<Future<Boolean>> explains = grants.stream()
                                                           .map(grant -> relationships.explains(applicationId, modelId, grant, subject, permission))
                                                           .toList();
                    return Future.all(explains).compose(results -> allowed.map(held -> {
                        List<Grant> through = new ArrayList<>();
                        for (int i = 0; i < grants.size(); i++) {
                            if (results.<Boolean>resultAt(i)) {
                                through.add(grants.get(i));
                            }
                        }
                        return new AccessExplanation(held, through);
                    }));
                }));
    }

    // The grants made on the resource, then on the application above a tenant
    private Future<List<Grant>> grantsOn(String applicationId, Resource resource) {
        Future<List<Grant>> ret = relationships.findGrants(applicationId, objectOf(resource));
        if (AuthzUtil.TENANT_TYPE.equals(resource.type())) {
            ret = ret.compose(own -> relationships.findGrants(applicationId, AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, applicationId))
                                                  .map(above -> {
                                                      List<Grant> all = new ArrayList<>(own);
                                                      all.addAll(above);
                                                      return all;
                                                  }));
        }
        return ret;
    }

    // A tenant is placed under its application when it is first granted on, so the grant and every one made on
    // the application reach it; the application itself is where the graph starts
    private Future<Void> contained(String applicationId, Resource resource) {
        Future<Void> ret;
        if (AuthzUtil.TENANT_TYPE.equals(resource.type())) {
            ret = relationships.ensure(applicationId, List.of(new RelationshipTuple(AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, applicationId),
                                                                                    AuthzUtil.APPLICATION_TYPE,
                                                                                    objectOf(resource))));
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    private Future<Application> requireApplication(String applicationId) {
        String organizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        return applications.findById(applicationId, organizationId)
                           .map(application -> DomainUtil.requireOwned(application, organizationId, "No application of the organization has id " + applicationId));
    }

    private Future<Void> requireRole(String applicationId, String roleId) {
        return relationships.read(applicationId, AuthzUtil.object(AuthzUtil.ROLE_TYPE, roleId)).map(held -> {
            if (held.isEmpty()) {
                throw new IllegalArgumentException("No role of the application has id " + roleId);
            }
            return null;
        });
    }

    // A grant in an application's store is made to one of its own users or machines
    private Future<Void> requireSubject(String applicationId, Subject subject) {
        return identities.findById(subject.id()).map(identity -> {
            if (!belongs(identity, applicationId)) {
                throw new IllegalArgumentException("No user or machine of the application has id " + subject.id());
            }
            return null;
        });
    }

    private static boolean belongs(ParticipantIdentity identity, String applicationId) {
        return identity != null && applicationId.equals(identity.getApplicationId());
    }

    private static void validate(String applicationId, Resource resource) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notNull(resource, "resource cannot be null");
        Validate.notBlank(resource.id(), "resource id cannot be blank");
        boolean theApplication = AuthzUtil.APPLICATION_TYPE.equals(resource.type()) && applicationId.equals(resource.id());
        Validate.isTrue(theApplication || AuthzUtil.TENANT_TYPE.equals(resource.type()),
                        "a grant in an application is made on the application itself or on one of its tenants, not on %s", resource);
    }

    // An application's store has no groups, so a subject is one of its identities
    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.kind() == SubjectKind.USER, "a grant in an application is made to a user or a machine");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

    private static String objectOf(Resource resource) {
        return AuthzUtil.object(resource.type(), resource.id());
    }

    private static String userOf(Subject subject) {
        return AuthzUtil.object(AuthzUtil.USER_TYPE, subject.id());
    }
}
