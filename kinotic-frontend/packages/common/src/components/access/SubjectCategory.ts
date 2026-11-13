import type { SubjectOption } from './SubjectOption'

/** One category of subject a grant drawer offers, such as members or groups, with the subjects in it. */
export interface SubjectCategory {
  label: string
  options: SubjectOption[]
  placeholder: string
  emptyMessage: string
}
