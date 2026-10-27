import type { AccessExplanation, Subject } from '@kinotic-ai/management-api'

/** Who a grant can be made to, as the pickers and the grant rows show them. */
export interface SubjectOption {
  subject: Subject
  /** The subject's name, such as a member's name or email, or a group's name. */
  label: string
  /** What is shown beneath the name, such as a member's email or a group's description. */
  detail: string | null
}

/** One kind of subject a grant drawer offers, such as members or groups, with the subjects of that kind. */
export interface SubjectKind {
  label: string
  options: SubjectOption[]
  placeholder: string
  emptyMessage: string
}

/** A check's answer as the check panel shows it: the explanation, and what was asked, read after "holds". */
export interface CheckedAccess {
  explanation: AccessExplanation
  /** What the subject was asked to hold, such as "read on this project". */
  asked: string
}
