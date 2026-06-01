package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcPendingSignUp;
import org.kinotic.domain.api.services.iam.OidcSignUpService;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.api.repositories.OidcPendingSignUpRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOidcSignUpService implements OidcSignUpService {

    private static final long DEFAULT_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private final OidcPendingSignUpRepository pendingSignUpRepository;
    private final IamUserRepository iamUserRepository;
    private final OrgAdminProvisioner orgAdminProvisioner;

    @Override
    public CompletableFuture<OidcPendingSignUp> create(OidcPendingSignUp registration) {
        Validate.notBlank(registration.getOidcSubject(), "oidcSubject is required");
        Validate.notBlank(registration.getOidcConfigId(), "oidcConfigId is required");
        Validate.notBlank(registration.getEmail(), "email is required");

        Date now = new Date();
        registration.setId(UUID.randomUUID().toString());
        registration.setVerificationToken(UUID.randomUUID().toString());
        registration.setCreated(now);
        registration.setExpiresAt(new Date(now.getTime() + DEFAULT_TTL_MS));

        return pendingSignUpRepository.saveSync(registration);
    }

    @Override
    public CompletableFuture<OidcPendingSignUp> findByToken(String verificationToken) {
        Validate.notBlank(verificationToken, "verificationToken is required");
        return pendingSignUpRepository.findByToken(verificationToken);
    }

    @Override
    public CompletableFuture<IamUser> complete(String verificationToken, Consumer<IamUser> finalizer) {
        return pendingSignUpRepository.findValidByToken(verificationToken)
                .thenCompose(pending -> {
                    IamUser user = buildUser(pending);
                    user.setOrganizationId(pending.getOrganizationId());
                    // A null organizationId leaves the user with both scope ids null — i.e. SYSTEM —
                    // and OIDC registration must never mint a platform operator. The caller of the
                    // REGISTRATION_REQUIRED path must populate organizationId up front.
                    Validate.notBlank(user.getOrganizationId(),
                                      "Cannot create user: pending sign-up has no organizationId");
                    if (finalizer != null) {
                        finalizer.accept(user);
                    }
                    return iamUserRepository.save(user)
                            .thenCompose(saved -> pendingSignUpRepository.deleteById(pending.getId())
                                    .thenApply(v -> saved));
                });
    }

    @Override
    public CompletableFuture<IamUser> completeWithNewOrg(String verificationToken, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "orgName is required");
        return pendingSignUpRepository.findValidByToken(verificationToken)
                .thenCompose(pending -> {
                    IamUser admin = buildUser(pending);
                    return orgAdminProvisioner.provisionNewOrg(orgName, orgDescription, admin)
                            .thenCompose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                    .thenApply(v -> savedAdmin));
                });
    }

    private IamUser buildUser(OidcPendingSignUp pending) {
        Date now = new Date();
        return new IamUser()
                .setId(UUID.randomUUID().toString())
                .setEmail(pending.getEmail())
                .setDisplayName(pending.getDisplayName())
                .setAuthType(AuthType.OIDC)
                .setOidcSubject(pending.getOidcSubject())
                .setOidcConfigId(pending.getOidcConfigId())
                .setEnabled(true)
                .setCreated(now)
                .setUpdated(now);
    }
}
