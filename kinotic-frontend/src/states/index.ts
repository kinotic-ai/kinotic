import type { Reactive } from 'vue'
import {APPLICATION_STATE, type IApplicationState} from './IApplicationState'
import {USER_STATE, type IUserState} from './IUserState'
import {INSIGHTS_STATE, type IInsightsState} from './IInsightsState'

export namespace StructuresStates {

    export function getApplicationState(): Reactive<IApplicationState> {
        return APPLICATION_STATE
    }

    export function getUserState(): IUserState {
        return USER_STATE
    }

    export function getInsightsState(): IInsightsState {
        return INSIGHTS_STATE
    }

}
