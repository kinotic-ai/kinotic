import type { SubjectSource } from './SubjectSource'

/** One tab of a grant drawer's subject picker, such as members or groups, with the subjects it offers. */
export interface SubjectPickerTab extends SubjectSource {
  placeholder: string
  emptyMessage: string
}
