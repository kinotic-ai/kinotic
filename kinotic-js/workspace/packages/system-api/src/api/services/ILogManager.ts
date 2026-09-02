export enum LogLevel {
    TRACE = 'TRACE',
    DEBUG = 'DEBUG',
    INFO = 'INFO',
    WARN = 'WARN',
    ERROR = 'ERROR',
    FATAL = 'FATAL',
    OFF = 'OFF'
}

export class LoggerLevelsDescriptor {
    public configuredLevel?: LogLevel;
}

export class GroupLoggerLevelsDescriptor extends LoggerLevelsDescriptor {
    public members: string[] = [];
}

export class SingleLoggerLevelsDescriptor extends LoggerLevelsDescriptor {
    public effectiveLevel?: LogLevel;
}

/**
 * Description of loggers
 */
export class LoggersDescriptor {
    public levels: LogLevel[] = []
    public loggerLevels: Map<string, SingleLoggerLevelsDescriptor> = new Map()
    public groups: Map<string, GroupLoggerLevelsDescriptor> = new Map()
}

/**
 * Provides the ability to manage loggers
 */
export interface ILogManager {

    /**
     * @param nodeId the kinotic node to get the LoggersDescriptor from
     * @return a {@link LoggersDescriptor} containing all the loggers and their levels
     */
    loggers(nodeId: string): Promise<LoggersDescriptor>

    /**
     * @param nodeId the kinotic node to get the LoggerLevelsDescriptor from
     * @param name the name of the logger to get
     * @return a {@link LoggerLevelsDescriptor} containing the logger and its levels
     */
    loggerLevels(nodeId: string, name: string): Promise<LoggerLevelsDescriptor>

    /**
     * Configures the log level for the logger with the given name
     * @param nodeId the kinotic node to set the log level on
     * @param name the name of the logger to set
     * @param level the {@link LogLevel} to set for the logger with the given name
     */
    configureLogLevel(nodeId: string, name: string, level: LogLevel): Promise<void>

    /**
     * @param nodeId the kinotic node to get the trace log excludes from
     * @return the CRI patterns currently excluded from trace logging on the node
     */
    traceLogExcludes(nodeId: string): Promise<string[]>

    /**
     * Configures the CRI patterns excluded from trace logging, silencing a service that would
     * otherwise bury the log while trace logging is on. Each pattern is matched against the fully
     * qualified CRI with Ant wildcards, so `srv://system-api~com.acme.HeartbeatService/*` covers
     * every method of that service and a raw CRI covers only the method it names.
     * The patterns replace whatever the node is using and last until it restarts, which returns it
     * to the `kinotic.traceLogExcludes` it was configured with.
     *
     * @param nodeId the kinotic node to set the trace log excludes on
     * @param excludes the CRI patterns to exclude, or empty to exclude nothing
     */
    configureTraceLogExcludes(nodeId: string, excludes: string[]): Promise<void>
}
