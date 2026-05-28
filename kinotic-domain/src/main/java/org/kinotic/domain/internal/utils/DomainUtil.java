package org.kinotic.domain.internal.utils;

import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.SystemParticipant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created By Navíd Mitchell 🤪on 2/13/26
 */
public class DomainUtil {

    private static final Pattern ApplicationPattern = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]*$");
    private static final Pattern ProjectIdPattern = Pattern.compile("^[a-z][a-z0-9._-]*$");
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Function will validate the structure application name
     *
     * @param applicationId to validate
     * @throws IllegalArgumentException will be thrown if the structure application is invalid
     */
    public static void validateApplicationId(String applicationId) {
        if (applicationId == null) {
            throw new IllegalArgumentException("Application Id must not be null");
        }
        if (!ApplicationPattern.matcher(applicationId).matches()){
            throw new IllegalArgumentException("Kinotic Application Id Invalid, first character must be a " +
                                                       "letter. And contain only letters, numbers, periods, underscores or dashes. Got "+ applicationId);
        }
    }

    public static void validateProjectId(String projectId){
        if(projectId == null){
            throw new IllegalArgumentException("Project Id must not be null");
        }
        if (!ProjectIdPattern.matcher(projectId).matches()){
            throw new IllegalArgumentException("Kinotic Project Id Invalid, first character must be a " +
                                                       "letter. And contain only letters, numbers, periods, underscores or dashes. Got "+ projectId);
        }
    }

    /**
     * Hashes the given raw password using BCrypt.
     *
     * @param rawPassword to hash
     * @return the BCrypt hash of the given password
     */
    public static String hashPassword(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * Verifies that the given raw password matches the given BCrypt hash.
     *
     * @param rawPassword to verify
     * @param hash        the BCrypt hash to verify against
     * @return true if the password matches the hash
     */
    public static boolean verifyPassword(String rawPassword, String hash) {
        return PASSWORD_ENCODER.matches(rawPassword, hash);
    }

    /**
     * Generates a high-entropy, URL-safe token from {@code numBytes} of secure random data.
     * Used for bearer secrets (device codes, refresh tokens) whose plaintext is shown to a
     * client once and only ever stored as a hash.
     *
     * @param numBytes number of random bytes to draw before base64url encoding
     * @return a base64url-encoded random token without padding
     */
    public static String generateUrlSafeToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Returns the SHA-256 hash of {@code value} as a lowercase hex string. Suitable for
     * at-rest storage of high-entropy bearer tokens — the entropy makes a fast unsalted
     * digest safe, and the deterministic output allows lookup by hash.
     *
     * @param value the value to hash
     * @return the SHA-256 digest as lowercase hex
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Builds the {@link Participant} security identity for an authenticated {@link IamUser}.
     * Single entry point used by both STOMP CONNECT auth and the browser session-login flow
     * so the resulting in-process identity is consistent across transports.
     * <p>
     * Dispatches to the typed subtype helper that matches {@code user.getAuthScopeType()} —
     * {@link SystemParticipant}, {@link OrganizationParticipant}, or
     * {@link ApplicationParticipant}. For APPLICATION-scope users, callers must instead
     * invoke {@link #createApplicationParticipant(IamUser, String)} directly with the
     * organization id; this dispatcher has no way to source one and throws.
     *
     * @param user the authenticated user
     * @return the participant for the given user
     * @throws IllegalStateException if {@code user} is APPLICATION-scoped or carries an
     *         unrecognized {@code authScopeType}
     */
    public static Participant createParticipant(IamUser user) {
        return switch (AuthScopeType.valueOf(user.getAuthScopeType())) {
            case SYSTEM -> createSystemParticipant(user);
            case ORGANIZATION -> createOrganizationParticipant(user);
            case APPLICATION -> throw new IllegalStateException(
                    "APP-scope IamUser '" + user.getId()
                    + "' requires an organizationId; call createApplicationParticipant(user, organizationId) directly.");
        };
    }

    public static SystemParticipant createSystemParticipant(IamUser user) {
        return DefaultSystemParticipant.builder()
                                       .id(user.getId())
                                       .metadata(buildParticipantMetadata(user))
                                       .roles(List.of())
                                       .build();
    }

    public static OrganizationParticipant createOrganizationParticipant(IamUser user) {
        return DefaultOrganizationParticipant.builder()
                                             .id(user.getId())
                                             .organizationId(user.getAuthScopeId())
                                             .metadata(buildParticipantMetadata(user))
                                             .roles(List.of())
                                             .build();
    }

    public static ApplicationParticipant createApplicationParticipant(IamUser user, String organizationId) {
        return DefaultApplicationParticipant.builder()
                                            .id(user.getId())
                                            .organizationId(organizationId)
                                            .applicationId(user.getAuthScopeId())
                                            .tenantId(user.getTenantId())
                                            .metadata(buildParticipantMetadata(user))
                                            .roles(List.of())
                                            .build();
    }

    private static Map<String, String> buildParticipantMetadata(IamUser user) {
        return Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authType", user.getAuthType().name()
        );
    }

}
