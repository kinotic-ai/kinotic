import { ref, type ComputedRef, type Ref } from 'vue'
import { type IterablePage, Kinotic, type Page, Pageable } from '@kinotic-ai/core'
import { type Group, type Subject, SubjectType } from '@kinotic-ai/management-api'
import { createDebug, isAuthorizationError, latestOnly } from '@kinotic-ai/frontend-common'
import { SUBJECT_PAGE_SIZE, SUBJECT_SEARCH_PAGE_SIZE, type SubjectOption, machineOption, useSubjectNames, userOption } from '@kinotic-ai/frontend-common/management'

const debug = createDebug('access')

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
  // the pickers list every group and machine and the first page of the members or users; a grant row names one
  // beyond it by its id until it has been searched for
  const { remember, labelOf } = useSubjectNames(() => [...members.value, ...groups.value, ...machines.value])
  const startLoad = latestOnly()

  async function load(): Promise<void> {
    const isLatest = startLoad()
    const application = applicationId()
    if (application === null) {
      await Promise.all([
        read(isLatest, 'members', firstPage(Kinotic.members.findMembers(Pageable.create(0, SUBJECT_PAGE_SIZE))), members, userOption),
        read(isLatest, 'groups', everyPage(Kinotic.permissions.findGroups(Pageable.create(0, SUBJECT_PAGE_SIZE))), groups, groupOption)
      ])
    } else {
      await Promise.all([
        read(isLatest, 'users', firstPage(Kinotic.members.findApplicationUsers(application, Pageable.create(0, SUBJECT_PAGE_SIZE))), members, userOption),
        read(isLatest, 'machines', everyPage(Kinotic.machines.findMachines(application, Pageable.create(0, SUBJECT_PAGE_SIZE))), machines, machineOption)
      ])
    }
  }

  // a list the caller may not read is left empty, and the panel shows the rest; any other failure is the caller's
  async function read<T>(isLatest: () => boolean, what: string, items: Promise<T[]>, into: Ref<SubjectOption[]>,
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
    if (isLatest()) {
      into.value = options
    }
  }

  /**
   * The organization's members or, for an application, its users whose name or email matches the text.
   */
  async function searchMembers(text: string): Promise<SubjectOption[]> {
    const application = applicationId()
    const pageable = Pageable.create(0, SUBJECT_SEARCH_PAGE_SIZE)
    const page = application === null
        ? await Kinotic.members.searchMembers(text, pageable)
        : await Kinotic.members.searchApplicationUsers(text, application, pageable)
    const found = (page.content ?? []).map(userOption)
    remember(found)
    return found
  }

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

function groupOption(group: Group): SubjectOption {
  return {
    subject: { type: SubjectType.GROUP, id: group.id ?? '' },
    label: group.name,
    detail: group.description
  }
}
