/**
 * What checkUiVersion found out about the commit a site serves, compared with the one an open
 * tab was built from.
 */
export interface UiVersionCheck {
    /**
     * True when the site serves a commit other than the one the tab was built from: the tab is
     * behind, and a reload brings the published commit.
     */
    stale: boolean
    /**
     * The commit the site serves, or null when its version could not be read, in which case the
     * tab is not reported stale.
     */
    servedCommit: string | null
}
