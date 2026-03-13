

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
     * @param nodeId the kinoitc node to get the LoggersDescriptor from
     * @return a {@link LoggersDescriptor} containing all the loggers and their levels
     */
    loggers(nodeId: string): Promise<LoggersDescriptor>

    /**
     * @param nodeId the kinoitc node to get the LoggerLevelsDescriptor from
     * @param name the name of the logger to get
     * @return a {@link LoggerLevelsDescriptor} containing the logger and its levels
     */
    loggerLevels(nodeId: string, name: string): Promise<LoggerLevelsDescriptor>

    /**
     * Configures the log level for the logger with the given name
     * @param nodeId the kinoitc node to set the log level on
     * @param name the name of the logger to set
     * @param level the {@link LogLevel} to set for the logger with the given name
     */
    configureLogLevel(nodeId: string, name: string, level: LogLevel): Promise<void>
}
