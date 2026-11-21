import type { AccessExplanation } from '@kinotic-ai/management-api'

/** A check's answer as the check panel shows it: the explanation, and what was asked, read after "holds". */
export interface CheckedAccess {
  explanation: AccessExplanation
  /** What the subject was asked to hold, such as "read on this project". */
  asked: string
}
