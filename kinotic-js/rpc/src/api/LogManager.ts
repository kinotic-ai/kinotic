/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
