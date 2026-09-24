import type { RouteLocationRaw } from 'vue-router'

/**
 * What a traffic summary reads: the invocations whose callers act for an organization, summed over
 * its applications, or for one of its applications, or every caller's when it names no
 * organization; and the page with the detail.
 */
export interface TrafficView {
  organizationId: string | null
  applicationId: string | null
  to: RouteLocationRaw
}
