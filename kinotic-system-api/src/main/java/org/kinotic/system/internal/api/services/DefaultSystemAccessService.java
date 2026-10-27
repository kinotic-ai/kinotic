package org.kinotic.system.internal.api.services;

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
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.system.api.services.SystemAccessService;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

@Component
@RequiredArgsConstructor
public class DefaultSystemAccessService implements SystemAccessService {

    private static final Resource SYSTEM_RESOURCE = new Resource(AuthzConstants.SYSTEM_TYPE, AuthzConstants.SYSTEM_OBJECT_ID);

    private final AuthzModelService models;
    private final RelationshipService relationships;
    private final ParticipantIdentityService identities;

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        return model().map(model -> model.builtInRoles(AuthzConstants.SYSTEM_TYPE));
    }

    @Override
    public Future<List<Grant>> findGrants() {
        return relationships.findGrants(SYSTEM, AuthzConstants.SYSTEM_OBJECT).map(grants -> grants.stream().map(AuthzGraphUtil::localGrant).toList());
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId) {
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        return model().compose(model -> {
            if (!model.roles().containsKey(roleId) || !model.grantable(roleId, AuthzConstants.SYSTEM_TYPE)) {
                throw new IllegalArgumentException("No role of the system has id " + roleId);
            }
            return requireStaff(subject);
        }).compose(stored -> relationships.bind(SYSTEM, roleId, AuthzGraphUtil.authzSubject(stored), AuthzConstants.SYSTEM_OBJECT))
          .map(bindingId -> new Grant(bindingId, roleId, subject, SYSTEM_RESOURCE));
    }

    @Override
    public Future<Void> revoke(String grantId) {
        Validate.notBlank(grantId, "grantId cannot be blank");
        return relationships.findGrants(SYSTEM, AuthzConstants.SYSTEM_OBJECT).compose(grants -> {
            List<Grant> administrators = grants.stream().filter(DefaultSystemAccessService::isAdministrator).toList();
            Future<Void> ret;
            if (administrators.stream().anyMatch(grant -> grant.id().equals(grantId))) {
                // a disabled administrator cannot sign in, so its grant keeps no one able to run the system; a
                // system with no administrator has every enabled system user made one by SystemGraphBootstrap on
                // the next start
                ret = enabledCount(administrators).compose(enabled -> {
                    if (enabled <= 1) {
                        throw new IllegalStateException("The grant " + grantId + " is the system's last enabled administrator;"
                                                                + " grant the role to another system user first");
                    }
                    return relationships.revoke(SYSTEM, grantId, AuthzConstants.SYSTEM_OBJECT);
                });
            } else {
                ret = relationships.revoke(SYSTEM, grantId, AuthzConstants.SYSTEM_OBJECT);
            }
            return ret;
        });
    }

    private static boolean isAdministrator(Grant grant) {
        return AuthzConstants.SYSTEM_ADMIN_ROLE.equals(grant.roleId());
    }

    // How many of the grants are held by an enabled identity
    private Future<Long> enabledCount(List<Grant> grants) {
        List<Future<Boolean>> lookups = grants.stream()
                                              .map(grant -> identities.findById(grant.subject().id())
                                                                      .map(identity -> identity != null && identity.isEnabled()))
                                              .toList();
        return Future.all(lookups).map(enabled -> enabled.list().stream().filter(Boolean.TRUE::equals).count());
    }

    @Override
    public Future<AccessExplanation> explain(Subject subject, String permission) {
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        String name = AuthzUtil.permissionName(AuthzConstants.SYSTEM_TYPE, permission);
        return requireStaff(subject).compose(stored -> relationships.findGrants(SYSTEM, AuthzConstants.SYSTEM_OBJECT)
                .compose(grants -> relationships.explain(SYSTEM, stored, new RelationshipTuple(AuthzGraphUtil.authzSubject(stored), name, AuthzConstants.SYSTEM_OBJECT),
                                                         List.of(), grants))
                .map(AuthzGraphUtil::localExplanation));
    }

    private Future<AuthzModel> model() {
        return models.findSystemModel();
    }

    // The system's staff, a system user or a machine in SYSTEM scope, each held by the store as itself
    private Future<Subject> requireStaff(Subject subject) {
        return identities.findById(subject.id()).map(identity -> {
            boolean staff = identity instanceof SystemParticipantIdentity
                    || (identity instanceof MachineParticipantIdentity machine
                        && machine.getOrganizationId() == null
                        && machine.getApplicationId() == null);
            if (!staff) {
                throw new IllegalArgumentException("No system user or machine of the system has id " + subject.id());
            }
            return subject;
        });
    }

    // The system has no groups, so a subject is one of its identities
    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.type() == SubjectType.USER, "a grant on the system is made to a user");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

}
