package org.kinotic.core.api.log;

/**
 * Created by Navíd Mitchell 🤪 on 4/5/23.
 */
public
enum LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF;

    /**
     * The level matching Spring Boot's, by name.
     *
     * @param level Spring Boot's level, or {@code null} for a logger that inherits its level
     * @return the matching level, or {@code null} when {@code level} is {@code null}
     */
    public static LogLevel of(org.springframework.boot.logging.LogLevel level) {
        return level == null ? null : LogLevel.valueOf(level.name());
    }
}
