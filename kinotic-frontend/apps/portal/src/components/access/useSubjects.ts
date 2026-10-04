import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { type Group, type Subject, SubjectKind, type UserParticipantIdentity } from '@kinotic-ai/management-api'

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
 * The organization's members and groups as grant subjects, read once per panel: the pickers list them and the
 * grant rows name them.
 */
export function useSubjects(): {
  members: Ref<SubjectOption[]>
  groups: Ref<SubjectOption[]>
  loaded: Ref<boolean>
  load: () => Promise<void>
  labelOf: ComputedRef<(subject: Subject) => string>
} {
  const members = ref<SubjectOption[]>([])
  const groups = ref<SubjectOption[]>([])
  const loaded = ref(false)

  async function load(): Promise<void> {
    const [memberPage, groupPage] = await Promise.all([
      Kinotic.members.findMembers(null, Pageable.create(0, SUBJECT_PAGE_SIZE)),
      Kinotic.permissions.findGroups(Pageable.create(0, SUBJECT_PAGE_SIZE))
    ])
    members.value = (memberPage.content ?? []).map(memberOption)
    groups.value = (groupPage.content ?? []).map(groupOption)
    loaded.value = true
  }

  const labelOf = computed(() => {
    const names = new Map<string, string>()
    for (const option of [...members.value, ...groups.value]) {
      names.set(keyOf(option.subject), option.label)
    }
    return (subject: Subject) => names.get(keyOf(subject)) ?? subject.id
  })

  return { members, groups, loaded, load, labelOf }
}

function memberOption(member: UserParticipantIdentity): SubjectOption {
  return {
    subject: { kind: SubjectKind.USER, id: member.id ?? '' },
    label: member.displayName || member.email,
    detail: member.displayName ? member.email : null
  }
}

function groupOption(group: Group): SubjectOption {
  return {
    subject: { kind: SubjectKind.GROUP, id: group.id ?? '' },
    label: group.name,
    detail: group.description
  }
}

function keyOf(subject: Subject): string {
  return `${subject.kind}:${subject.id}`
}
