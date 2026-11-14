import { ref, type ComputedRef, type Ref } from 'vue'
import { FunctionalIterablePage, Kinotic, Pageable } from '@kinotic-ai/core'
import type { MachineParticipantIdentity, Subject } from '@kinotic-ai/management-api'
import { SUBJECT_PAGE_SIZE, SUBJECT_SEARCH_PAGE_SIZE, type SubjectOption, machineOption, useSubjectNames, userOption } from '@kinotic-ai/frontend-common/management'

/** Who a system grant can be made to, as the pickers and the grant rows show them. */
export interface SystemSubjectOption extends SubjectOption {
  /** What the subject is: a system user who signs in, or a machine that connects. */
  staff: 'System user' | 'Machine'
}

/**
 * The system users and machines as grant subjects, read once per page: the pickers list them and the
 * grant rows name them, with the system users a search found.
 */
export function useSubjects(): {
  systemUsers: Ref<SystemSubjectOption[]>
  machines: Ref<SystemSubjectOption[]>
  load: () => Promise<void>
  searchSystemUsers: (text: string) => Promise<SystemSubjectOption[]>
  optionOf: ComputedRef<(subject: Subject) => SystemSubjectOption | undefined>
  labelOf: ComputedRef<(subject: Subject) => string>
} {
  const systemUsers = ref<SystemSubjectOption[]>([])
  const machines = ref<SystemSubjectOption[]>([])
  // the pickers list every machine and the first page of the system users; a grant row names one beyond it by
  // its id until it has been searched for
  const { remember, optionOf, labelOf } = useSubjectNames(() => [...systemUsers.value, ...machines.value])

  async function load(): Promise<void> {
    const [userPage, everyMachine] = await Promise.all([
      Kinotic.systemMembers.findUsers(Pageable.create(0, SUBJECT_PAGE_SIZE)),
      findEveryMachine()
    ])
    systemUsers.value = (userPage.content ?? []).map((user): SystemSubjectOption => ({ ...userOption(user), staff: 'System user' }))
    machines.value = everyMachine.map((machine): SystemSubjectOption => ({ ...machineOption(machine), staff: 'Machine' }))
  }

  // the machines have no search on the server, so the pickers list all of them
  async function findEveryMachine(): Promise<MachineParticipantIdentity[]> {
    const pageable = Pageable.create(0, SUBJECT_PAGE_SIZE)
    const first = await Kinotic.systemMembers.findMachines(pageable)
    const found: MachineParticipantIdentity[] = []
    for await (const page of new FunctionalIterablePage(pageable, first, next => Kinotic.systemMembers.findMachines(next))) {
      found.push(...(page.content ?? []))
    }
    return found
  }

  /** The system users whose name or email matches the text. */
  async function searchSystemUsers(text: string): Promise<SystemSubjectOption[]> {
    const page = await Kinotic.systemMembers.searchUsers(text, Pageable.create(0, SUBJECT_SEARCH_PAGE_SIZE))
    const found = (page.content ?? []).map((user): SystemSubjectOption => ({ ...userOption(user), staff: 'System user' }))
    remember(found)
    return found
  }

  return { systemUsers, machines, load, searchSystemUsers, optionOf, labelOf }
}
