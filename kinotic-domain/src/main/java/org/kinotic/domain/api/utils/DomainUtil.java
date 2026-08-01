package org.kinotic.domain.api.utils;

import com.github.slugify.Slugify;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created By Navíd Mitchell 🤪on 2/13/26
 */
public class DomainUtil {

    /**
     * The zone for platform services organizations use to manage the system, such as member,
     * application, and entity definition management
     */
    public static final String OS_API_ZONE = "os-api";

    /**
     * The zone for the platform's application facing data services, such as entity persistence
     * and named query execution
     */
    public static final String APP_API_ZONE = "app-api";

    /**
     * The zone for services internal to the platform, only reachable by system participants
     */
    public static final String SYSTEM_ZONE = "system";

    /**
     * The leading label of application zones, which follow the form
     * kinotic-app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    public static final String APP_ZONE_PREFIX = "kinotic-app";

    // Project ids may start with a digit because they embed application ids, which may
    // themselves start with a digit
    private static final Pattern ProjectIdPattern = Pattern.compile("^[a-z0-9][a-z0-9.-]*$");
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // Dash separator, not underscore: slugified ids become zone labels, and underscores are
    // illegal in a URI host (CRIs are valid URIs by convention)
    private static final Slugify SLUGIFY = Slugify.builder().build();

    /**
     * Validates that the given application id contains only lowercase letters, digits, and
     * interior dashes, and is not a zone label the platform reserves for itself.
     *
     * @param applicationId to validate
     * @throws IllegalArgumentException if the application id is null, invalid, or reserved
     */
    public static void validateApplicationId(String applicationId) {
        if (applicationId == null) {
            throw new IllegalArgumentException("Application Id must not be null");
        }
        validateZoneLabelId(applicationId);
    }

    /**
     * Validates that the given organization id contains only lowercase letters, digits, and
     * interior dashes, and is not a zone label the platform reserves for itself.
     *
     * @param organizationId to validate
     * @throws IllegalArgumentException if the organization id is null, invalid, or reserved
     */
    public static void validateOrganizationId(String organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization Id must not be null");
        }
        validateZoneLabelId(organizationId);
    }

    private static void validateZoneLabelId(String id) {
        ZoneUtil.validateLabel(id);
        Validate.isTrue(!SYSTEM_ZONE.equals(id), "Id '%s' is reserved by the platform", id);
    }

    public static void validateProjectId(String projectId){
        if(projectId == null){
            throw new IllegalArgumentException("Project Id must not be null");
        }
        if (!ProjectIdPattern.matcher(projectId).matches()){
            throw new IllegalArgumentException("Kinotic Project Id Invalid, first character must be a " +
                                                       "letter or number. And contain only letters, numbers, periods or dashes. Got "+ projectId);
        }
    }

    /**
     * Derives an id from the given text, slugified to lowercase letters, digits, and interior
     * dashes.
     *
     * @param text to derive the id from
     * @return the derived id
     * @throws IllegalArgumentException if the text is blank or has no usable characters
     */
    public static String slugifyId(String text) {
        Validate.notBlank(text, "Cannot derive an id from a blank value");
        // Slugify keeps separators it produced at the edges ("Acme Inc." -> "acme-inc-"), but
        // the id must start and end alphanumeric
        String id = StringUtils.strip(SLUGIFY.slugify(text), "-");
        Validate.notEmpty(id, "Cannot derive an id from '%s', it has no usable characters", text);
        // Guards against a Slugify behavior change; the version in use only emits lowercase
        // letters, digits, and dashes
        ZoneUtil.validateLabel(id);
        return id;
    }

    /**
     * Normalizes an email address to its canonical stored form: trimmed and lowercased
     * with {@link Locale#ROOT} (locale-sensitive lowercasing corrupts emails under e.g.
     * a Turkish default locale). Emails are matched with exact-match term filters, so
     * every write and every lookup must agree on this one form.
     *
     * @param email to normalize, must not be blank
     * @return the normalized email
     * @throws IllegalArgumentException if {@code email} is blank
     * @throws NullPointerException if {@code email} is null
     */
    public static String normalizeEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return email.trim().toLowerCase(Locale.ROOT);
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
     * Returns the typed subtype that matches the user's structural scope:
     * <ul>
     *   <li>{@code organizationId == null} → {@link DefaultSystemParticipant}</li>
     *   <li>{@code organizationId != null, applicationId == null} → {@link DefaultOrganizationParticipant}</li>
     *   <li>both set → {@link DefaultApplicationParticipant} (also carrying {@code tenantId})</li>
     * </ul>
     * Synchronous: every field needed lives on the user, no lookups required.
     *
     * @param user the authenticated user
     * @return the typed participant for the given user
     */
    public static Participant createParticipant(IamUser user) {
        Map<String, String> metadata = Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authType", user.getAuthType().name()
        );
        if (user.getOrganizationId() == null) {
            return DefaultSystemParticipant.builder()
                                           .id(user.getId())
                                           .metadata(metadata)
                                           .roles(List.of())
                                           .build();
        }
        if (user.getApplicationId() == null) {
            return DefaultOrganizationParticipant.builder()
                                                 .id(user.getId())
                                                 .organizationId(user.getOrganizationId())
                                                 .metadata(metadata)
                                                 .roles(List.of())
                                                 .build();
        }
        return DefaultApplicationParticipant.builder()
                                            .id(user.getId())
                                            .organizationId(user.getOrganizationId())
                                            .applicationId(user.getApplicationId())
                                            .tenantId(user.getTenantId())
                                            .metadata(metadata)
                                            .roles(List.of())
                                            .build();
    }

}
