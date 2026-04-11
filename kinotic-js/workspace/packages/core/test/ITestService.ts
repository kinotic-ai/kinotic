import {Kinotic, KinoticSingleton, IServiceProxy} from '../src'

export interface ITestService {

    testMethodWithString(value: string): Promise<string>;

    testMissingMethod(): Promise<void>;

    getTestUUID(): Promise<string>;

    getParticipantIdFromContext(): Promise<string>;

    getParticipantIdFromContextViaDispatch(): Promise<string>;

    getParticipantIdFromContextInExecuteBlocking(): Promise<string>;

    verifyParticipantParameterMatchesContext(): Promise<string>;

    getFullParticipantFromContext(): Promise<Record<string, any>>;

    getParticipantOnlyParam(): Promise<Record<string, any>>;

    getParticipantIdFromMonoChain(): Promise<string>;

    getParticipantIdFromNestedExecuteBlocking(): Promise<string>;

    getParticipantIdRepeated(count: number): Promise<string[]>;

    participantFirstArgWithContext(suffix: string): Promise<string>;

    participantLastArgWithContext(prefix: string): Promise<string>;

    verifyParticipantInMonoChain(): Promise<string>;

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

    verifyParticipantParameterMatchesContext(): Promise<string> {
        return this.serviceProxy.invoke('verifyParticipantParameterMatchesContext')
    }

    getFullParticipantFromContext(): Promise<Record<string, any>> {
        return this.serviceProxy.invoke('getFullParticipantFromContext')
    }

    getParticipantOnlyParam(): Promise<Record<string, any>> {
        return this.serviceProxy.invoke('getParticipantOnlyParam')
    }

    getParticipantIdFromMonoChain(): Promise<string> {
        return this.serviceProxy.invoke('getParticipantIdFromMonoChain')
    }

    getParticipantIdFromNestedExecuteBlocking(): Promise<string> {
        return this.serviceProxy.invoke('getParticipantIdFromNestedExecuteBlocking')
    }

    getParticipantIdRepeated(count: number): Promise<string[]> {
        return this.serviceProxy.invoke('getParticipantIdRepeated', [count])
    }

    participantFirstArgWithContext(suffix: string): Promise<string> {
        return this.serviceProxy.invoke('participantFirstArgWithContext', [suffix])
    }

    participantLastArgWithContext(prefix: string): Promise<string> {
        return this.serviceProxy.invoke('participantLastArgWithContext', [prefix])
    }

    verifyParticipantInMonoChain(): Promise<string> {
        return this.serviceProxy.invoke('verifyParticipantInMonoChain')
    }
}

export const TEST_SERVICE: ITestService = new TestService()
