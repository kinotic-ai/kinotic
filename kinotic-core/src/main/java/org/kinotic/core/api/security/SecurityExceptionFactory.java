package org.kinotic.core.api.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.utils.KinoticUtil;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Component;

/**
 * Builds the security exceptions a caller is refused with. Each answers the caller with a generic message, and
 * with {@link KinoticProperties#isDebug()} on, the reason follows it, which can reveal server implementation
 * details. The reason is logged either way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionFactory {

    private static final String NOT_AUTHORIZED = "Not authorized";

    private final KinoticProperties properties;

    /**
     * @param reason    why the caller is refused, with a {@code {}} placeholder for each argument
     * @param arguments the values the placeholders stand for
     * @return the refusal, reading "Not authorized", or "Not authorized: " and the reason in debug mode
     */
    public AuthorizationException notAuthorized(String reason, Object... arguments) {
        // a string argument may come from the caller, so it is logged on one line and cannot forge a log entry
        Object[] logged = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            logged[i] = arguments[i] instanceof String value ? KinoticUtil.singleLine(value) : arguments[i];
        }
        log.warn(reason, logged);
        return new AuthorizationException(properties.isDebug()
                                                  ? NOT_AUTHORIZED + ": " + MessageFormatter.basicArrayFormat(reason, arguments)
                                                  : NOT_AUTHORIZED);
    }
}
