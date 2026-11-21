import type { Subject } from '@kinotic-ai/management-api'

/** Who a grant can be made to, as the pickers and the grant rows show them. */
export interface SubjectOption {
  subject: Subject
  /** The subject's name, such as a member's name or email, or a group's name. */
  label: string
  /** What is shown beneath the name, such as a member's email or a group's description. */
  detail: string | null
}
