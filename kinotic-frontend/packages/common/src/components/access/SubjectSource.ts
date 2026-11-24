import type { SubjectOption } from './SubjectOption'

/** The subjects of one kind a picker offers, such as an organization's members or its groups. */
export interface SubjectSource {
  label: string
  /** The subjects listed before anything is typed: all of them, or the first of more than the picker lists. */
  options: SubjectOption[]
  /**
   * Finds the subjects matching what was typed, for a source whose options are only the first of many. A source
   * without it is searched among its options.
   */
  search?: (text: string) => Promise<SubjectOption[]>
}
