package org.kinotic.core.api.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Component;

/**
 * Builds the security exceptions a caller is refused with. Each answers the caller with a generic message, and
 * with {@link KinoticProperties#isDebug()} on, the reason follows it, which can reveal server implementation
 * details. The reason is logged either way, at {@link Level#WARN} unless a level is given.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionFactory {

    private static final String NOT_AUTHORIZED = "Not authorized";
    private static final String NOT_AUTHENTICATED = "Not authenticated";

    private final KinoticProperties properties;

    /**
     * @param reason    why the caller is refused, with a {@code {}} placeholder for each argument
     * @param arguments the values the placeholders stand for
     * @return the refusal, reading "Not authorized", or "Not authorized: " and the reason in debug mode
     */
    public AuthorizationException notAuthorized(String reason, Object... arguments) {
        return notAuthorized(Level.WARN, reason, arguments);
    }

    /**
     * @param level     the level the reason is logged at
     * @param reason    why the caller is refused, with a {@code {}} placeholder for each argument
     * @param arguments the values the placeholders stand for
     * @return the refusal, reading "Not authorized", or "Not authorized: " and the reason in debug mode
     */
    public AuthorizationException notAuthorized(Level level, String reason, Object... arguments) {
        return new AuthorizationException(answer(NOT_AUTHORIZED, level, reason, arguments));
    }

    /**
     * @param reason    why the caller is not authenticated, with a {@code {}} placeholder for each argument
     * @param arguments the values the placeholders stand for
     * @return the refusal, reading "Not authenticated", or "Not authenticated: " and the reason in debug mode
     */
    public AuthenticationException notAuthenticated(String reason, Object... arguments) {
        return notAuthenticated(Level.WARN, reason, arguments);
    }

    /**
     * @param level     the level the reason is logged at
     * @param reason    why the caller is not authenticated, with a {@code {}} placeholder for each argument
     * @param arguments the values the placeholders stand for
     * @return the refusal, reading "Not authenticated", or "Not authenticated: " and the reason in debug mode
     */
    public AuthenticationException notAuthenticated(Level level, String reason, Object... arguments) {
        return new AuthenticationException(answer(NOT_AUTHENTICATED, level, reason, arguments));
    }

    // Logs the reason and returns the message the caller is answered with
    private String answer(String refusal, Level level, String reason, Object[] arguments) {
        log.atLevel(level).log(reason, arguments);
        return properties.isDebug() ? refusal + ": " + MessageFormatter.basicArrayFormat(reason, arguments) : refusal;
    }
}
