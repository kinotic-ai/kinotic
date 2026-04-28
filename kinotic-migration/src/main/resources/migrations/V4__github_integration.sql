-- GitHub App installations: one row per Kinotic Org that has linked GitHub.
CREATE TABLE IF NOT EXISTS kinotic_github_app_installation (
    id KEYWORD,
    organizationId KEYWORD,
    githubInstallationId KEYWORD,
    accountLogin KEYWORD,
    accountType KEYWORD,
    suspendedAt DATE,
    created DATE,
    updated DATE
);

-- Project ↔ repo links: one row per Kinotic Project linked to a GitHub repo.
CREATE TABLE IF NOT EXISTS kinotic_project_github_repo (
    id KEYWORD,
    projectId KEYWORD,
    organizationId KEYWORD,
    installationId KEYWORD,
    repoFullName KEYWORD,
    repoId KEYWORD,
    defaultBranch KEYWORD,
    updated DATE
);
