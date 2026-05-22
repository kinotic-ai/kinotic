package org.kinotic.domain.internal.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
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

}
