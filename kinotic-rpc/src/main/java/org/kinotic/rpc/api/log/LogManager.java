

package org.kinotic.rpc.api.log;

import org.kinotic.rpc.api.annotations.Scope;

/**
 * Interface providing the ability to work with runtime logging configuration per node
 *
 * Created by Navid Mitchell 🤪 on 7/9/20
 */
//@Publish FIXME: add RBAC for this
//@Version("0.1.0")
public interface LogManager {

    @Scope
    String nodeId();

    /**
     * @return a {@link LoggersDescriptor} containing all the loggers and their levels
     */
    LoggersDescriptor loggers();

    /**
     * @param name the name of the logger to get
     * @return a {@link LoggerLevelsDescriptor} containing the logger and its levels
     */
    LoggerLevelsDescriptor loggerLevels(String name);

    /**
     * Configures the log level for the logger with the given name
     * @param name the name of the logger to set
     * @param level the {@link LogLevel} to set for the logger with the given name
     */
    void configureLogLevel(String name, LogLevel level);

}
