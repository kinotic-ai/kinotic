package org.kinotic.os.api.model.log;

/**
 * Created by Navíd Mitchell 🤪 on 4/5/23.
 */
public
enum LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF;

    public static LogLevel fromString(String level) {
        if (level == null) {
            throw new IllegalArgumentException("LogLevel cannot be null");
        }
        return LogLevel.valueOf(level.toUpperCase());
    }
}
