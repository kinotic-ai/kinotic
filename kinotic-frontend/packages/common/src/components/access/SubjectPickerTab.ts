import type { SubjectOption } from './SubjectOption'

/** One tab of a grant drawer's subject picker, such as members or groups, with the subjects it offers. */
export interface SubjectPickerTab {
  label: string
  options: SubjectOption[]
  placeholder: string
  emptyMessage: string
}
