import { ref, type Ref } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { KinoticStates } from '@/states'
import { createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('access')

const canManageAccess = ref(false)
const loaded = ref(false)
// the member the answer was read for; another member signing in on the same page reads their own
let loadedFor: string | null = null

/**
 * Whether the signed-in member may manage the organization's access: roles, groups and grants. Read once per
 * member and shared, so every Access panel and settings tab shows the same controls; a member who may not
 * sees the lists and the check panel and no write control. A read that fails leaves the controls hidden.
 */
export function useAccess(): { canManageAccess: Ref<boolean>, loaded: Ref<boolean> } {
  const userState = KinoticStates.getUserState()
  const memberId = userState.connectedInfo?.participant?.id ?? null
  if (memberId !== null && memberId !== loadedFor) {
    loadedFor = memberId
    loaded.value = false
    canManageAccess.value = false
    const organizationId = userState.getOrganizationId()
    Kinotic.permissions.listAccessible('organization', 'can_manage_access')
           .then(ids => { canManageAccess.value = ids.includes(organizationId) })
           .catch(err => debug('Failed to read whether the member manages access: %O', err))
           .finally(() => { loaded.value = true })
  }
  return { canManageAccess, loaded }
}
