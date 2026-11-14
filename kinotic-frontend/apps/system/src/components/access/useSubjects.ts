import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { type MachineParticipantIdentity, type Subject, SubjectType, type SystemParticipantIdentity } from '@kinotic-ai/management-api'
import type { SubjectOption } from '@kinotic-ai/frontend-common/management'

/** Who a system grant can be made to, as the pickers and the grant rows show them. */
export interface SystemSubjectOption extends SubjectOption {
  /** What the subject is: a system user who signs in, or a machine that connects. */
  staff: 'System user' | 'Machine'
}

// The pickers list, and the grant rows name, the first pages of the system users and machines, which cover a
// system of a few hundred of each; a system user beyond them is found by searching, and a grant row names it by
// its id until it has been searched for
const SUBJECT_PAGE_SIZE = 500
// the matches a search lists; typing more narrows them
const SEARCH_PAGE_SIZE = 50

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
  // the system users a search found, by id, which the list read at load may not hold
  const searched = ref(new Map<string, SystemSubjectOption>())

  async function load(): Promise<void> {
    const [userPage, machinePage] = await Promise.all([
      Kinotic.systemMembers.findUsers(Pageable.create(0, SUBJECT_PAGE_SIZE)),
      Kinotic.systemMembers.findMachines(Pageable.create(0, SUBJECT_PAGE_SIZE))
    ])
    systemUsers.value = (userPage.content ?? []).map(systemUserOption)
    machines.value = (machinePage.content ?? []).map(machineOption)
  }

  /** The system users whose name or email matches the text. */
  async function searchSystemUsers(text: string): Promise<SystemSubjectOption[]> {
    const page = await Kinotic.systemMembers.searchUsers(text, Pageable.create(0, SEARCH_PAGE_SIZE))
    const found = (page.content ?? []).map(systemUserOption)
    searched.value = new Map([...searched.value, ...found.map(option => [option.subject.id, option] as const)])
    return found
  }

  const optionOf = computed(() => {
    const options = new Map<string, SystemSubjectOption>()
    for (const option of [...searched.value.values(), ...systemUsers.value, ...machines.value]) {
      options.set(option.subject.id, option)
    }
    return (subject: Subject) => options.get(subject.id)
  })

  const labelOf = computed(() => (subject: Subject) => optionOf.value(subject)?.label ?? subject.id)

  return { systemUsers, machines, load, searchSystemUsers, optionOf, labelOf }
}

function systemUserOption(user: SystemParticipantIdentity): SystemSubjectOption {
  return {
    subject: { type: SubjectType.USER, id: user.id ?? '' },
    label: user.displayName || user.email,
    detail: user.displayName ? user.email : null,
    staff: 'System user'
  }
}

function machineOption(machine: MachineParticipantIdentity): SystemSubjectOption {
  return {
    subject: { type: SubjectType.USER, id: machine.id ?? '' },
    label: machine.displayName || machine.id || '',
    detail: machine.displayName ? machine.id ?? null : null,
    staff: 'Machine'
  }
}
