package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.system.api.services.SystemAccessService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

@Component
@RequiredArgsConstructor
public class DefaultSystemAccessService implements SystemAccessService {

    private static final Resource PLATFORM_RESOURCE = new Resource(AuthzUtil.PLATFORM_TYPE, AuthzUtil.PLATFORM_OBJECT_ID);
    private static final String PLATFORM_OBJECT = AuthzUtil.object(AuthzUtil.PLATFORM_TYPE, AuthzUtil.PLATFORM_OBJECT_ID);

    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;
    private final ParticipantIdentityService identities;

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        return model().map(model -> {
            List<RoleDefinition> ret = new ArrayList<>();
            model.roles().forEach((id, permissions) -> ret.add(new RoleDefinition(id, AuthzUtil.builtInRoleName(id), null, true, permissions)));
            return ret;
        });
    }

    @Override
    public Future<List<Grant>> findGrants() {
        return relationships.findGrants(PLATFORM, PLATFORM_OBJECT);
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId) {
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        return model().compose(model -> {
            if (!model.roles().containsKey(roleId)) {
                throw new IllegalArgumentException("No role of the platform has id " + roleId);
            }
            return requireStaff(subject);
        }).compose(v -> relationships.bind(PLATFORM, roleId, userOf(subject), PLATFORM_OBJECT))
          .map(bindingId -> new Grant(bindingId, roleId, subject, PLATFORM_RESOURCE));
    }

    @Override
    public Future<Void> revoke(String grantId) {
        Validate.notBlank(grantId, "grantId cannot be blank");
        return relationships.revoke(PLATFORM, grantId, PLATFORM_OBJECT);
    }

    @Override
    public Future<AccessExplanation> explain(Subject subject, String permission) {
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        String name = AuthzUtil.permissionName(AuthzUtil.PLATFORM_TYPE, permission);
        RelationshipTuple holds = new RelationshipTuple(userOf(subject), name, PLATFORM_OBJECT);
        return requireStaff(subject)
                .compose(v -> findGrants())
                .compose(grants -> stores.modelId(PLATFORM).compose(modelId -> {
                    // an administrator asks after changing access, so the answer must not predate the change
                    Future<Boolean> allowed = relationships.check(PLATFORM, modelId, holds, Consistency.HIGHER_CONSISTENCY);
                    List<Future<Boolean>> explains = grants.stream().map(grant -> relationships.explains(PLATFORM, modelId, grant, subject, name)).toList();
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

    private Future<AuthzModel> model() {
        return directory.findSystemContracts().map(generator::platformModel);
    }

    // The platform's staff are its own identities: an operator or a machine in SYSTEM scope
    private Future<Void> requireStaff(Subject subject) {
        return identities.findById(subject.id()).map(identity -> {
            if (identity == null || identity.getOrganizationId() != null || identity.getApplicationId() != null) {
                throw new IllegalArgumentException("No operator or machine of the platform has id " + subject.id());
            }
            return null;
        });
    }

    // The platform has no groups, so a subject is one of its identities
    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.kind() == SubjectKind.USER, "a grant on the platform is made to a user");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

    private static String userOf(Subject subject) {
        return AuthzUtil.object(AuthzUtil.USER_TYPE, subject.id());
    }
}
