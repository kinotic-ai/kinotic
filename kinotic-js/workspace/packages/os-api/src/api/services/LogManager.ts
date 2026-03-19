import {
    type ILogManager,
    LogLevel,
    LoggersDescriptor,
    LoggerLevelsDescriptor,
    SingleLoggerLevelsDescriptor,
    GroupLoggerLevelsDescriptor} from './ILogManager'
import {type IKinotic, type IServiceProxy} from '@kinotic-ai/core'

export class LogManager implements ILogManager {
    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('org.kinotic.os.api.services.LogManager')
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

