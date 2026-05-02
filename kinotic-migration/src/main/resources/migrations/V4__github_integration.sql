-- GitHub App installations: one row per Kinotic Org that has linked GitHub.
CREATE TABLE IF NOT EXISTS kinotic_github_app_installation (
    id KEYWORD,
    organizationId KEYWORD,
    githubInstallationId LONG,
    accountLogin KEYWORD,
    accountType KEYWORD,
    suspendedAt DATE,
    created DATE,
    updated DATE
);

-- Project ↔ repo metadata: every Kinotic Project is backed by a GitHub repo.
-- Provisioned at project create time; never manually linked.
ALTER TABLE kinotic_project ADD COLUMN repoFullName KEYWORD;
ALTER TABLE kinotic_project ADD COLUMN repoId LONG;
ALTER TABLE kinotic_project ADD COLUMN defaultBranch KEYWORD;
ALTER TABLE kinotic_project ADD COLUMN repoPrivate BOOLEAN;
