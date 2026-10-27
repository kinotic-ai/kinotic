import type { SubjectOption } from './SubjectOption'

/** One kind of subject a grant drawer offers, such as members or groups, with the subjects of that kind. */
export interface SubjectKind {
  label: string
  options: SubjectOption[]
  placeholder: string
  emptyMessage: string
}
