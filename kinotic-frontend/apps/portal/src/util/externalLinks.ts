/** The Kinotic documentation, opened from the header's Help link and the search dialog. */
export const DOCUMENTATION_URL = 'https://kinotic.ai/apps/introduction'

/** A form in `.github/ISSUE_TEMPLATE` of the Kinotic repository. */
export type IssueTemplate = 'bug_report.yml' | 'feature_request.yml'

/**
 * GitHub's new-issue page on the Kinotic repository for `template`, with its page field set to `pagePattern`
 * and, for a bug, its browser field set. The issue is public, so `pagePattern` is a route pattern, which names
 * no application or project.
 */
export function newIssueUrl(template: IssueTemplate, pagePattern: string): string {
  const params = new URLSearchParams({ template, page: pagePattern })
  if (template === 'bug_report.yml') {
    params.set('browser', navigator.userAgent)
  }
  return `https://github.com/kinotic-ai/kinotic/issues/new?${params}`
}
