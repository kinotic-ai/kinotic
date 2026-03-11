

import { ILogManager,
    LogLevel,
    LoggersDescriptor,
    LoggerLevelsDescriptor,
    SingleLoggerLevelsDescriptor,
    GroupLoggerLevelsDescriptor} from './ILogManager'
import { IServiceProxy } from '@/core/api/IServiceRegistry'
import { Continuum } from '@/api/Continuum'

export class LogManager implements ILogManager {
    private readonly serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Continuum.serviceProxy('org.kinotic.continuum.api.log.LogManager')
    }

    loggers(nodeId: string): Promise<LoggersDescriptor> {
        return this.serviceProxy.invoke('loggers', null, nodeId)
    }

    async loggerLevels(nodeId: string, name: string): Promise<LoggerLevelsDescriptor> {
        const data: any = await this.serviceProxy.invoke('loggerLevels', [name], nodeId)
        let ret: LoggerLevelsDescriptor | null = null;
        if(data.hasOwnProperty('members')) {
            ret = new GroupLoggerLevelsDescriptor()
        }else if(data.hasOwnProperty('effectiveLevel')) {
            ret = new SingleLoggerLevelsDescriptor()
        }else{
            ret = new LoggerLevelsDescriptor()
        }
        Object.assign(ret, data)
        return ret
    }

    configureLogLevel(nodeId: string, name: string, level: LogLevel): Promise<void> {
        return this.serviceProxy.invoke('configureLogLevel', [name, level], nodeId)
    }
}

export const logManager = new LogManager()
