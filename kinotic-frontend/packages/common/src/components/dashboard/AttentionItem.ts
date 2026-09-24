/** One thing someone has to look at, and where it is handled. */
export interface AttentionItem {
  severity: 'danger' | 'warn'
  icon: string
  text: string
  detail: string
  to: string
}
