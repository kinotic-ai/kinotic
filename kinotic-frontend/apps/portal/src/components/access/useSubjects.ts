import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { type ApplicationParticipantIdentity, type Group, type MachineParticipantIdentity, type OrganizationParticipantIdentity, type Subject,
  SubjectType } from '@kinotic-ai/management-api'

/** Who a grant can be made to, as the pickers and the grant rows show them. */
export interface SubjectOption {
  subject: Subject
  /** The member's name or email, or the group's name. */
  label: string
  /** The member's email beneath the name, or the group's description. */
  detail: string | null
}

// The pickers and the names on the grant rows read the first pages of the organization's members and groups,
// which cover an organization of a few hundred members; a subject beyond them is shown by its id
const SUBJECT_PAGE_SIZE = 500

/**
 * Who a grant can be made to, read once per panel: the organization's members and groups, as the pickers of
 * the system store's panels list them, or, for an application, its users and machines, as the pickers of the
 * application's own store list them; the grant rows name whichever were read.
 *
 * @param applicationId the application whose users and machines are the subjects, or null for the organization's
 *                      members and groups
 */
export function useSubjects(applicationId: string | null = null): {
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
    if (applicationId === null) {
      const memberPage = await Kinotic.members.findMembers(Pageable.create(0, SUBJECT_PAGE_SIZE))
      members.value = (memberPage.content ?? []).map(memberOption)
      const groupPage = await Kinotic.permissions.findGroups(Pageable.create(0, SUBJECT_PAGE_SIZE))
      groups.value = (groupPage.content ?? []).map(groupOption)
    } else {
      const userPage = await Kinotic.members.findApplicationUsers(applicationId, Pageable.create(0, SUBJECT_PAGE_SIZE))
      members.value = (userPage.content ?? []).map(memberOption)
      const machinePage = await Kinotic.machines.findMachines(applicationId, Pageable.create(0, SUBJECT_PAGE_SIZE))
      machines.value = (machinePage.content ?? []).map(machineOption)
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

/** The key a subject is told apart by: a user and a group may share an id. */
export function keyOf(subject: Subject): string {
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
