import type { Reactive } from 'vue'
import {APPLICATION_STATE, type IApplicationState} from './IApplicationState'
import {USER_STATE, type IUserState} from './IUserState'

export namespace KinoticStates {

    export function getApplicationState(): Reactive<IApplicationState> {
        return APPLICATION_STATE
    }

    export function getUserState(): IUserState {
        return USER_STATE
    }

}
