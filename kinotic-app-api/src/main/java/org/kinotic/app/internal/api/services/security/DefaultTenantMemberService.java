package org.kinotic.app.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.PendingInviteSummary;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.app.api.services.security.TenantMemberService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultTenantMemberService implements TenantMemberService {

    private final SecurityContext securityContext;
    private final ApplicationRepository applications;
    private final ParticipantIdentityService identities;
    private final InviteService invites;
    private final RelationshipService relationships;

    @Override
    public Future<Page<UserParticipantIdentity>> findMembers(Pageable pageable) {
        ApplicationParticipant caller = caller();
        return identities.findUsersByTenant(caller.getOrganizationId(), caller.getApplicationId(), caller.getTenantId(), pageable);
    }

    @Override
    public Future<PendingInviteSummary> inviteMember(String email, String displayName) {
        Validate.notBlank(email, "email is required");
        ApplicationParticipant caller = caller();
        return applications.findById(caller.getApplicationId(), caller.getOrganizationId())
                           .compose(application -> {
                               if (application == null || !application.getOnboarding().contains(OnboardingMechanism.TENANT_INVITE)) {
                                   throw new IllegalStateException("This application does not offer invitations into a tenant.");
                               }
                               return invites.createInvite(new PendingInvite().setEmail(email)
                                                                              .setDisplayName(displayName)
                                                                              .setOrganizationId(caller.getOrganizationId())
                                                                              .setApplicationId(caller.getApplicationId())
                                                                              .setTenantId(caller.getTenantId())
                                                                              .setInvitedById(caller.getId())
                                                                              .setInvitedByName(DomainUtil.displayNameOf(caller)));
                           })
                           .map(PendingInviteSummary::from);
    }

    @Override
    public Future<Page<PendingInviteSummary>> findPendingInvites(Pageable pageable) {
        ApplicationParticipant caller = caller();
        return invites.findPendingInvitesByTenant(caller.getOrganizationId(), caller.getApplicationId(), caller.getTenantId(), pageable)
                      .map(page -> page.map(PendingInviteSummary::from));
    }

    @Override
    public Future<Void> cancelInvite(String inviteId) {
        ApplicationParticipant caller = caller();
        return invites.cancelTenantInvite(inviteId, caller.getOrganizationId(), caller.getApplicationId(), caller.getTenantId());
    }

    @Override
    public Future<Void> removeMember(String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        ApplicationParticipant caller = caller();
        if (identityId.equals(caller.getId())) {
            return Future.failedFuture(new IllegalArgumentException("You cannot perform this action on your own account."));
        }
        return identities.findById(identityId)
                         // a missing user and one outside the tenant fail the same way, so there is no existence oracle
                         .map(identity -> DomainUtil.requireOwned(identity, UserParticipantIdentity.class,
                                                                  user -> inTenant(user, caller), "Member not found."))
                         // cascades the credential; sync so an immediate re-query no longer lists the member
                         .compose(user -> identities.deleteByIdSync(user.getId()));
    }

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        ApplicationParticipant caller = caller();
        // a grant on a tenant confers nothing of the application's own, so the application's roles are not offered
        return relationships.findRoles(caller.getApplicationId())
                            .map(roles -> roles.stream()
                                               .filter(role -> !role.id().startsWith(AuthzUtil.APPLICATION_TYPE + "."))
                                               .toList());
    }

    @Override
    public Future<List<Grant>> findGrants() {
        ApplicationParticipant caller = caller();
        return relationships.findGrants(caller.getApplicationId(), tenantObject(caller));
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.isTrue(subject.kind() == SubjectKind.USER, "a grant on a tenant is made to one of its users");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
        Validate.notBlank(roleId, "roleId cannot be blank");
        ApplicationParticipant caller = caller();
        return findRoles().compose(roles -> {
                              DomainUtil.requireRole(roles, roleId);
                              return identities.findById(subject.id());
                          })
                          .compose(identity -> {
                              if (!(identity instanceof UserParticipantIdentity user) || !inTenant(user, caller)) {
                                  throw new IllegalArgumentException("No user of the tenant has id " + subject.id());
                              }
                              return relationships.bind(caller.getApplicationId(), roleId,
                                                        AuthzUtil.object(AuthzUtil.USER_TYPE, subject.id()), tenantObject(caller));
                          })
                          .map(bindingId -> new Grant(bindingId, roleId, subject, new Resource(AuthzUtil.TENANT_TYPE, caller.getTenantId())));
    }

    @Override
    public Future<Void> revoke(String grantId) {
        Validate.notBlank(grantId, "grantId cannot be blank");
        ApplicationParticipant caller = caller();
        return relationships.revoke(caller.getApplicationId(), grantId, tenantObject(caller));
    }

    // The gateway checks a caller outside every tenant on its application, so the tenant is required here too
    private ApplicationParticipant caller() {
        ApplicationParticipant ret = securityContext.requireParticipant(ApplicationParticipant.class);
        DomainUtil.requireTenant(ret);
        return ret;
    }

    private static boolean inTenant(UserParticipantIdentity user, ApplicationParticipant caller) {
        return caller.getApplicationId().equals(user.getApplicationId()) && caller.getTenantId().equals(user.getTenantId());
    }

    private static String tenantObject(ApplicationParticipant caller) {
        return AuthzUtil.object(AuthzUtil.TENANT_TYPE, caller.getTenantId());
    }
}
