package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.management.api.model.AddApplicationUserRequest;
import org.kinotic.management.api.services.security.MemberInvitationService;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DefaultMemberInvitationService implements MemberInvitationService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final OidcConfigurationService oidcConfigurationService;
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    @Override
    public Future<OrganizationParticipantIdentity> addOrganizationUser(String email, String displayName, boolean sendInvitation,
                                                                       EmailTemplate emailTemplate) {
        OrganizationParticipant inviter = requireOrgParticipant();
        Validate.notBlank(email, "email is required");
        String address = DomainUtil.normalizeEmail(email);
        String organizationId = inviter.getOrganizationId();
        if (sendInvitation) {
            // checked before the member the invitation is for is saved
            emailService.validateTemplate(emailTemplate);
        }
        // an invitation lets the address claim the member, and without one only the organization's own provider can,
        // so it must have one
        Future<Void> admissible = sendInvitation ? Future.succeededFuture() : requireOwnProvider(organizationId);
        OrganizationParticipantIdentity user = new OrganizationParticipantIdentity();
        user.setOrganizationId(organizationId)
            .setEmail(address)
            .setPending(true)
            .setDisplayName(StringUtils.trimToNull(displayName));
        return admissible.compose(v -> identityService.createUser(user, null))
                         .compose(created -> {
                             Future<Void> invited = sendInvitation
                                     ? invite(inviter, address, displayName, created, emailTemplate).mapEmpty()
                                     : Future.succeededFuture();
                             return invited.map(created);
                         });
    }

    private Future<Void> requireOwnProvider(String organizationId) {
        return oidcConfigurationService.findEnabledForScope(organizationId, null).map(offered -> {
            Validate.isTrue(offered.stream().anyMatch(OrganizationOidcConfiguration.class::isInstance),
                            "Without an invitation a new member can sign in only through one of your organization's own"
                                    + " sign-in providers; configure one or send the invitation.");
            return null;
        });
    }

    @Override
    public Future<ApplicationParticipantIdentity> addApplicationUser(AddApplicationUserRequest request, EmailTemplate emailTemplate) {
        OrganizationParticipant inviter = requireOrgParticipant();
        Validate.notNull(request, "request is required");
        Validate.isTrue(StringUtils.isNotBlank(request.getEmail()), "email is required");
        Validate.isTrue(StringUtils.isNotBlank(request.getApplicationId()), "applicationId is required");
        String address = DomainUtil.normalizeEmail(request.getEmail());
        String organizationId = inviter.getOrganizationId();
        String tenantId = request.getTenantId();
        // the user persists references to the application and its tenant, so both must be the organization's
        return applicationRepository.requireById(request.getApplicationId(), organizationId)
                                    .compose(application -> requireTenant(application, tenantId).compose(v -> {
                                        Future<ApplicationParticipantIdentity> ret;
                                        if (request.isSendInvitation()) {
                                            PendingInvite invite = invitation(inviter, address, request.getDisplayName())
                                                    .setApplicationId(application.getId())
                                                    .setTenantId(tenantId);
                                            ret = inviteService.inviteApplicationUser(invite, emailTemplate);
                                        } else {
                                            ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
                                            user.setOrganizationId(organizationId)
                                                .setApplicationId(application.getId())
                                                .setTenantId(tenantId)
                                                .setEmail(address)
                                                .setPending(true)
                                                .setDisplayName(StringUtils.trimToNull(request.getDisplayName()));
                                            ret = requireProviderSignIn(application, tenantId).compose(offered -> identityService.createUser(user, null));
                                        }
                                        return ret;
                                    }));
    }

    // the system gives each user a tenant of its own; otherwise the user joins one of the application's tenants
    private Future<Void> requireTenant(Application application, String tenantId) {
        Future<Void> ret;
        if (application.getTenantManagement() == TenantManagement.SYSTEM) {
            Validate.isTrue(tenantId == null, "The system gives each user of this application a tenant of its own, so none is named.");
            ret = Future.succeededFuture();
        } else {
            Validate.isTrue(StringUtils.isNotBlank(tenantId), "Name the tenant the user joins; this application's administrators manage its tenants.");
            ret = tenantRepository.findById(application.getId(), tenantId, application.getOrganizationId()).map(found -> {
                Validate.isTrue(found != null, "The application has no tenant %s.", tenantId);
                return null;
            });
        }
        return ret;
    }

    // a person not invited claims the user by signing in at the login of its tenant, which offers the tenant's own
    // providers and the application's
    private Future<Void> requireProviderSignIn(Application application, String tenantId) {
        return oidcConfigurationService.findEnabledForScope(application.getOrganizationId(), application.getId(), tenantId).map(offered -> {
            Validate.isTrue(!offered.isEmpty(), "Without an invitation a new user can sign in only through one of the application's"
                                                + " sign-in providers; configure one or send the invitation.");
            return null;
        });
    }

    @Override
    public Future<Void> sendInvitation(String email, String applicationId, EmailTemplate emailTemplate) {
        OrganizationParticipant inviter = requireOrgParticipant();
        Validate.notBlank(email, "email is required");
        String organizationId = inviter.getOrganizationId();
        String address = DomainUtil.normalizeEmail(email);
        return pendingUser(address, organizationId, applicationId)
                .compose(user -> {
                    // an application's user is sent only an invitation they can accept
                    Future<Void> acceptable = user instanceof ApplicationParticipantIdentity applicationUser
                            ? inviteService.requireAcceptable(organizationId, applicationId, applicationUser.getTenantId())
                            : Future.succeededFuture();
                    return acceptable.compose(v -> inviteService.findPendingInvite(address, organizationId, applicationId))
                                     .compose(pending -> pending != null
                                             ? inviteService.resendInvite(pending.getId(), organizationId, emailTemplate)
                                             // someone added without an invitation gets one now, to claim their user with
                                             : invite(inviter, address, user.getDisplayName(), user, emailTemplate).mapEmpty());
                });
    }

    // the pending user of the scope at the address
    private Future<UserParticipantIdentity> pendingUser(String address, String organizationId, String applicationId) {
        return identityService.findUserByEmail(address, organizationId, applicationId).map(found -> {
            Validate.isTrue(found != null, "No pending user has this email.");
            Validate.isTrue(found.isPending(), "This user has already signed in.");
            return found;
        });
    }

    // the invitation to claim user, whose scope it names
    private Future<PendingInvite> invite(OrganizationParticipant inviter, String email, String displayName,
                                         UserParticipantIdentity user, EmailTemplate emailTemplate) {
        PendingInvite invite = invitation(inviter, email, displayName);
        if (user instanceof ApplicationParticipantIdentity applicationUser) {
            invite.setApplicationId(applicationUser.getApplicationId())
                  .setTenantId(applicationUser.getTenantId());
        }
        return inviteService.createInvite(invite, emailTemplate);
    }

    // an invitation into the inviter's organization, sent in the inviter's name
    private static PendingInvite invitation(OrganizationParticipant inviter, String email, String displayName) {
        return new PendingInvite().setEmail(email)
                                  .setDisplayName(StringUtils.trimToNull(displayName))
                                  .setOrganizationId(inviter.getOrganizationId())
                                  .setInvitedById(inviter.getId())
                                  .setInvitedByName(DomainUtil.senderNameOf(inviter));
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }
}
