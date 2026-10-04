import { ref, type Ref } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { SubjectKind } from '@kinotic-ai/management-api'
import { createDebug, isAuthorizationError } from '@kinotic-ai/frontend-common'
import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

const debug = createDebug('access')

const canViewAccess = ref(false)
const canManageAccess = ref(false)
const loaded = ref(false)
// the operator the answer was read for; another operator signing in on the same page reads their own
let loadedFor: string | null = null

/**
 * What the signed-in operator may do with the platform's access: see its grants, and manage them. Read once
 * per operator by asking the platform's access service about the operator themself, and shared, so every
 * control agrees. An operator the service refuses holds no grant that sees access, and sees neither.
 */
export function useAccess(): { canViewAccess: Ref<boolean>, canManageAccess: Ref<boolean>, loaded: Ref<boolean> } {
  const operatorId = SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? null
  if (operatorId !== null && operatorId !== loadedFor) {
    loadedFor = operatorId
    loaded.value = false
    canViewAccess.value = false
    canManageAccess.value = false
    Kinotic.systemAccess.explain({ kind: SubjectKind.USER, id: operatorId }, 'can_manage_access')
           .then(explanation => {
             canViewAccess.value = true
             canManageAccess.value = explanation.allowed
           })
           .catch(err => {
             if (!isAuthorizationError(err)) {
               debug('Failed to read whether the operator manages access: %O', err)
             }
           })
           .finally(() => { loaded.value = true })
  }
  return { canViewAccess, canManageAccess, loaded }
}
