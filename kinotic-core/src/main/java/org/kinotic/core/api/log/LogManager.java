package org.kinotic.core.api.log;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.config.TraceLogProperties;

/**
 * Interface providing the ability to work with runtime logging configuration per node. Every server node
 * publishes it, scoped to its node id. It has no zone, so only system participants, which may send to any
 * zone, can call it.
 *
 * Created by Navid Mitchell 🤪 on 7/9/20
 */
@Publish
@Version("1.0.0")
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

    /**
     * @return the CRI patterns currently deciding what this node trace logs
     */
    TraceLogProperties traceLog();

    /**
     * Configures the CRI patterns deciding what this node trace logs, silencing a service that
     * would otherwise bury the log while trace logging is on.
     * The patterns replace whatever this node is using and last until it restarts, which returns it
     * to the {@code kinotic.traceLog} it was configured with.
     *
     * @param traceLog the include and exclude patterns to apply
     */
    void configureTraceLog(TraceLogProperties traceLog);

}
