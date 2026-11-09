import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { Kinotic, type Page, Pageable } from '@kinotic-ai/core'
import { type ApplicationParticipantIdentity, type Group, type MachineParticipantIdentity, type OrganizationParticipantIdentity, type Subject,
  SubjectType } from '@kinotic-ai/management-api'
import { createDebug } from '@kinotic-ai/frontend-common'
import type { SubjectOption } from '@kinotic-ai/frontend-common/management'

const debug = createDebug('access')

// The pickers and the names on the grant rows read the first pages of the organization's members and groups,
// which cover an organization of a few hundred members; a subject beyond them is shown by its id
const SUBJECT_PAGE_SIZE = 500

/**
 * Who a grant can be made to, read once per panel: the organization's members and groups, as the pickers of
 * the system store's panels list them, or, for an application, its users and machines, as the pickers of the
 * application's own store list them; the grant rows name whichever were read. Each list is read on its own,
 * so a caller who may read access but not the members still sees the grants, with such a subject named by its
 * id.
 *
 * @param applicationId reads the application whose users and machines are the subjects, or null for the
 *                      organization's members and groups; read on every load, so a page that switches application
 *                      in place reads the new application's subjects
 */
export function useSubjects(applicationId: () => string | null = () => null): {
  members: Ref<SubjectOption[]>
  groups: Ref<SubjectOption[]>
  machines: Ref<SubjectOption[]>
  load: () => Promise<void>
  labelOf: ComputedRef<(subject: Subject) => string>
} {
  const members = ref<SubjectOption[]>([])
  const groups = ref<SubjectOption[]>([])
  const machines = ref<SubjectOption[]>([])

  async function load(): Promise<void> {
    const application = applicationId()
    if (application === null) {
      await Promise.all([
        read('members', Kinotic.members.findMembers(Pageable.create(0, SUBJECT_PAGE_SIZE)), members, memberOption),
        read('groups', Kinotic.permissions.findGroups(Pageable.create(0, SUBJECT_PAGE_SIZE)), groups, groupOption)
      ])
    } else {
      await Promise.all([
        read('users', Kinotic.members.findApplicationUsers(application, Pageable.create(0, SUBJECT_PAGE_SIZE)), members, memberOption),
        read('machines', Kinotic.machines.findMachines(application, Pageable.create(0, SUBJECT_PAGE_SIZE)), machines, machineOption)
      ])
    }
  }

  // a list the caller may not read is left empty, and the panel shows the rest
  async function read<T>(what: string, page: Promise<Page<T>>, into: Ref<SubjectOption[]>, option: (item: T) => SubjectOption): Promise<void> {
    try {
      into.value = ((await page).content ?? []).map(option)
    } catch (err) {
      debug('Failed to read the %s a grant can be made to: %O', what, err)
      into.value = []
    }
  }

  const labelOf = computed(() => {
    const names = new Map<string, string>()
    for (const option of [...members.value, ...groups.value, ...machines.value]) {
      names.set(keyOf(option.subject), option.label)
    }
    return (subject: Subject) => names.get(keyOf(subject)) ?? subject.id
  })

  return { members, groups, machines, load, labelOf }
}

// the key a subject is told apart by: a user and a group may share an id
function keyOf(subject: Subject): string {
  return `${subject.type}:${subject.id}`
}

function memberOption(member: OrganizationParticipantIdentity | ApplicationParticipantIdentity): SubjectOption {
  return {
    subject: { type: SubjectType.USER, id: member.id ?? '' },
    label: member.displayName || member.email,
    detail: member.displayName ? member.email : null
  }
}

// a machine is a user subject to the store, named as the Machines page names it
function machineOption(machine: MachineParticipantIdentity): SubjectOption {
  return {
    subject: { type: SubjectType.USER, id: machine.id ?? '' },
    label: machine.displayName || machine.id || '',
    detail: machine.displayName ? machine.id ?? null : null
  }
}

function groupOption(group: Group): SubjectOption {
  return {
    subject: { type: SubjectType.GROUP, id: group.id ?? '' },
    label: group.name,
    detail: group.description
  }
}
