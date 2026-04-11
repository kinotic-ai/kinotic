import {Kinotic, KinoticSingleton, IServiceProxy} from '../src'

export interface ITestService {

    testMethodWithString(value: string): Promise<string>;

    testMissingMethod(): Promise<void>;

    getTestUUID(): Promise<string>;

    getParticipantIdFromContext(): Promise<string>;

    getParticipantIdFromContextViaDispatch(): Promise<string>;

    getParticipantIdFromContextInExecuteBlocking(): Promise<string>;

}

export class TestService implements ITestService {

    private readonly serviceProxy: IServiceProxy

    constructor(continuum?: KinoticSingleton) {
        let toUse = continuum || Kinotic
        this.serviceProxy = toUse.serviceProxy('org.kinotic.server.clienttest.ITestService')
    }

    testMethodWithString(value: string): Promise<string> {
        return this.serviceProxy.invoke('testMethodWithString', [value])
    }

    testMissingMethod(): Promise<void> {
        return this.serviceProxy.invoke('testMissingMethod')
    }

    getTestUUID(): Promise<string> {
        return this.serviceProxy.invoke('getTestUUID')
    }

    getParticipantIdFromContext(): Promise<string> {
        return this.serviceProxy.invoke('getParticipantIdFromContext')
    }

    getParticipantIdFromContextViaDispatch(): Promise<string> {
        return this.serviceProxy.invoke('getParticipantIdFromContextViaDispatch')
    }

    getParticipantIdFromContextInExecuteBlocking(): Promise<string> {
        return this.serviceProxy.invoke('getParticipantIdFromContextInExecuteBlocking')
    }
}

export const TEST_SERVICE: ITestService = new TestService()
