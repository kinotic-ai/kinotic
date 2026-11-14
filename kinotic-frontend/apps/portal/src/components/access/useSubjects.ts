import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { type IterablePage, Kinotic, type Page, Pageable } from '@kinotic-ai/core'
import { type ApplicationParticipantIdentity, type Group, type MachineParticipantIdentity, type OrganizationParticipantIdentity, type Subject,
  SubjectType } from '@kinotic-ai/management-api'
import { createDebug, isAuthorizationError } from '@kinotic-ai/frontend-common'
import type { SubjectOption } from '@kinotic-ai/frontend-common/management'

const debug = createDebug('access')

// The pickers list, and the grant rows name, every group and machine and the first page of the organization's
// members or the application's users, which covers a few hundred of them; a member or a user beyond it is found
// by searching, and a grant row names it by its id until it has been searched for
const SUBJECT_PAGE_SIZE = 500
// the matches a search lists; typing more narrows them
const SEARCH_PAGE_SIZE = 50

/**
 * Who a grant can be made to, read once per panel: the organization's members and groups, as the pickers of
 * the system store's panels list them, or, for an application, its users and machines, as the pickers of the
 * application's own store list them; the grant rows name whichever were read or searched for. Each list is read on its own,
 * and one the caller may not read is left empty, so a caller who may read access but not the members still
 * sees the grants, with such a subject named by its id; any other failure to read a list fails the load. Only
 * the latest load's lists are kept, so a page that switches application while a load is in flight shows the
 * subjects of the application it switched to.
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
  searchMembers: (text: string) => Promise<SubjectOption[]>
  labelOf: ComputedRef<(subject: Subject) => string>
} {
  const members = ref<SubjectOption[]>([])
  const groups = ref<SubjectOption[]>([])
  const machines = ref<SubjectOption[]>([])
  // the members and users a search found, by key, which the lists read at load may not hold
  const searched = ref(new Map<string, SubjectOption>())

  let latestLoad = 0

  async function load(): Promise<void> {
    const thisLoad = ++latestLoad
    const application = applicationId()
    if (application === null) {
      await Promise.all([
        read(thisLoad, 'members', firstPage(Kinotic.members.findMembers(Pageable.create(0, SUBJECT_PAGE_SIZE))), members, memberOption),
        read(thisLoad, 'groups', everyPage(Kinotic.permissions.findGroups(Pageable.create(0, SUBJECT_PAGE_SIZE))), groups, groupOption)
      ])
    } else {
      await Promise.all([
        read(thisLoad, 'users', firstPage(Kinotic.members.findApplicationUsers(application, Pageable.create(0, SUBJECT_PAGE_SIZE))), members, memberOption),
        read(thisLoad, 'machines', everyPage(Kinotic.machines.findMachines(application, Pageable.create(0, SUBJECT_PAGE_SIZE))), machines, machineOption)
      ])
    }
  }

  // a list the caller may not read is left empty, and the panel shows the rest; any other failure is the caller's
  async function read<T>(thisLoad: number, what: string, items: Promise<T[]>, into: Ref<SubjectOption[]>,
                         option: (item: T) => SubjectOption): Promise<void> {
    let options: SubjectOption[] = []
    try {
      options = (await items).map(option)
    } catch (err) {
      if (!isAuthorizationError(err)) {
        throw err
      }
      debug('The caller may not read the %s a grant can be made to: %O', what, err)
    }
    if (thisLoad === latestLoad) {
      into.value = options
    }
  }

  /**
   * The organization's members or, for an application, its users whose name or email matches the text.
   */
  async function searchMembers(text: string): Promise<SubjectOption[]> {
    const application = applicationId()
    const pageable = Pageable.create(0, SEARCH_PAGE_SIZE)
    const page = application === null
        ? await Kinotic.members.searchMembers(text, pageable)
        : await Kinotic.members.searchApplicationUsers(text, application, pageable)
    const found = (page.content ?? []).map(memberOption)
    searched.value = new Map([...searched.value, ...found.map(option => [keyOf(option.subject), option] as const)])
    return found
  }

  const labelOf = computed(() => {
    const names = new Map<string, string>()
    for (const option of [...searched.value.values(), ...members.value, ...groups.value, ...machines.value]) {
      names.set(keyOf(option.subject), option.label)
    }
    return (subject: Subject) => names.get(keyOf(subject)) ?? subject.id
  })

  return { members, groups, machines, load, searchMembers, labelOf }
}

async function firstPage<T>(page: Promise<Page<T>>): Promise<T[]> {
  return (await page).content ?? []
}

// the groups and the machines have no search on the server, so the pickers list all of them
async function everyPage<T>(pages: Promise<IterablePage<T>>): Promise<T[]> {
  const items: T[] = []
  for await (const page of await pages) {
    items.push(...(page.content ?? []))
  }
  return items
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
