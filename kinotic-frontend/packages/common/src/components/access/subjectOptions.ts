import { computed, shallowRef, type ComputedRef } from 'vue'
import { type MachineParticipantIdentity, type Subject, SubjectType } from '@kinotic-ai/management-api'
import type { SubjectOption } from './SubjectOption'

/**
 * How many users a picker lists before anything is typed, which covers a few hundred of them; a user beyond them
 * is found by searching.
 */
export const SUBJECT_PAGE_SIZE = 500
/** How many matches a search lists; typing more narrows them. */
export const SUBJECT_SEARCH_PAGE_SIZE = 50

/** A user as a grant subject, named by its display name, or by its email when it has none. */
export function userOption(user: { id?: string | null, displayName?: string | null, email: string }): SubjectOption {
  return {
    subject: { type: SubjectType.USER, id: user.id ?? '' },
    label: user.displayName || user.email,
    detail: user.displayName ? user.email : null
  }
}

/** A machine as a grant subject, a user to the store, named as the Machines page names it. */
export function machineOption(machine: MachineParticipantIdentity): SubjectOption {
  return {
    subject: { type: SubjectType.USER, id: machine.id ?? '' },
    label: machine.displayName || machine.id || '',
    detail: machine.displayName ? machine.id ?? null : null
  }
}

/**
 * The subjects a page can name on its grant rows: those its pickers list, and those a search found, which the
 * lists may not hold.
 *
 * @param listed reads the options the pickers list
 */
export function useSubjectNames<T extends SubjectOption>(listed: () => T[]): {
  /** Keeps the options a search found, so the subjects among them can be named. */
  remember: (found: T[]) => void
  /** The option of a subject, or undefined for one neither listed nor found by a search. */
  optionOf: ComputedRef<(subject: Subject) => T | undefined>
  /** The name of a subject, or its id for one neither listed nor found by a search. */
  labelOf: ComputedRef<(subject: Subject) => string>
} {
  const searched = shallowRef(new Map<string, T>())

  function remember(found: T[]): void {
    searched.value = new Map([...searched.value, ...found.map(option => [keyOf(option.subject), option] as const)])
  }

  const optionOf = computed(() => {
    const options = new Map<string, T>()
    for (const option of [...searched.value.values(), ...listed()]) {
      options.set(keyOf(option.subject), option)
    }
    return (subject: Subject) => options.get(keyOf(subject))
  })

  const labelOf = computed(() => (subject: Subject) => optionOf.value(subject)?.label ?? subject.id)

  return { remember, optionOf, labelOf }
}

// the key a subject is told apart by: a user and a group may share an id
function keyOf(subject: Subject): string {
  return `${subject.type}:${subject.id}`
}
