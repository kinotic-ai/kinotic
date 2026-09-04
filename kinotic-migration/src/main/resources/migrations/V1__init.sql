-- Create the application table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_application (
    id KEYWORD,
    organizationId KEYWORD,
    name KEYWORD,
    description TEXT,
    oidcConfigurationIds KEYWORD,
    tenantPerUser BOOLEAN,
    updated DATE
);

-- Create the named_query_service_definition table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_named_query_service_definition (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    entityDefinitionName KEYWORD,
    namedQueries JSON NOT INDEXED
);

-- Create the project table if it does not exist.
-- repoFullName / repoId / repoDefaultBranch are stamped by the GitHub repo provisioner
-- when the project is created; every project is backed by a GitHub repo.
CREATE TABLE IF NOT EXISTS kinotic_project (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    name KEYWORD,
    description TEXT,
    sourceOfTruth KEYWORD,
    repoFullName KEYWORD,
    repoId LONG,
    repoDefaultBranch KEYWORD,
    repoPrivate BOOLEAN,
    repoConnectionStatus KEYWORD,
    updated DATE
);

-- Deployment state per project: which node holds the checkout, which workload serves it,
-- and the commit currently live. One row per project; id equals the projectId.
CREATE TABLE IF NOT EXISTS kinotic_project_deployment (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    nodeId KEYWORD,
    hostDir KEYWORD,
    syncWorkloadId KEYWORD,
    uiPublishWorkloadId KEYWORD,
    syncMachineIdentityId KEYWORD,
    commitSha KEYWORD,
    artifacts OBJECT (
        microservices OBJECT (name KEYWORD, dir KEYWORD, entry KEYWORD),
        uis OBJECT (name KEYWORD, dir KEYWORD)
    ),
    artifactsCommitSha KEYWORD,
    lastJobRunId KEYWORD,
    status OBJECT (type KEYWORD, message TEXT),
    created DATE,
    updated DATE
);

-- Microservice deployments: one row per microservice artifact a project deployment has ensured.
CREATE TABLE IF NOT EXISTS kinotic_microservice_deployment (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    name KEYWORD,
    workloadId KEYWORD,
    machineIdentityId KEYWORD,
    entryPoint KEYWORD,
    commitSha KEYWORD,
    status OBJECT (type KEYWORD, message TEXT),
    created DATE,
    updated DATE
);

-- UI deployments: one row per UI artifact a project deployment has published, keyed by the
-- site's hostname label.
CREATE TABLE IF NOT EXISTS kinotic_ui_deployment (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    name KEYWORD,
    url KEYWORD,
    commitSha KEYWORD,
    status OBJECT (type KEYWORD, message TEXT),
    created DATE,
    updated DATE
);

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

-- Create the EntityDefinition table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_entity_definition (
    id KEYWORD,
    name KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    description TEXT,
    multiTenancyType KEYWORD,
    entityType KEYWORD,
    schema JSON NOT INDEXED,
    created DATE,
    updated DATE,
    published BOOLEAN,
    publishedTimestamp DATE,
    itemIndex KEYWORD,
    decoratedProperties JSON NOT INDEXED,
    versionFieldName KEYWORD NOT INDEXED,
    tenantIdFieldName KEYWORD NOT INDEXED,
    timeReferenceFieldName KEYWORD NOT INDEXED
);

-- Create the service_directory table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_service_directory (
    id KEYWORD,
    serviceAddress KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    namespace KEYWORD,
    name KEYWORD,
    version KEYWORD,
    zone KEYWORD,
    description TEXT,
    serviceDefinition JSON NOT INDEXED,
    advertised BOOLEAN,
    mcpExposed BOOLEAN,
    mcpTools OBJECT (
        name KEYWORD,
        title KEYWORD,
        description TEXT,
        inputSchema JSON NOT INDEXED,
        cri KEYWORD NOT INDEXED,
        annotations OBJECT (
            readOnlyHint BOOLEAN,
            destructiveHint BOOLEAN,
            idempotentHint BOOLEAN
        ) NOT INDEXED
    ),
    online BOOLEAN,
    lastStatusChange DATE
);

-- Participant Identity: authenticated identities at each scope layer — a person (type=USER)
-- or a client acting on a person's behalf (type=DELEGATE). Scope is encoded structurally by
-- which of organizationId / applicationId is set: both null = SYSTEM, organizationId only =
-- ORGANIZATION, both set = APPLICATION.
-- Uniqueness rules (enforced in service layer): one USER per (email, organizationId,
-- applicationId); one DELEGATE per (ownerId, clientKey).
CREATE TABLE IF NOT EXISTS kinotic_participant_identity (
    id KEYWORD,
    type KEYWORD,
    email KEYWORD,
    displayName KEYWORD,
    authType KEYWORD,
    oidcSubject KEYWORD,
    oidcConfigId KEYWORD,
    ownerId KEYWORD,
    clientKey KEYWORD,
    delegateKind KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    tenantId KEYWORD,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- IAM Credential: password hashes stored separately from user entities
CREATE TABLE IF NOT EXISTS kinotic_identity_credential (
    id KEYWORD,
    secretHash KEYWORD NOT INDEXED
);

-- OIDC Configuration: per-org OIDC provider configs. Each row is owned by an organization
-- (organizationId enforced by AbstractCrudService). "Where can this config be used" is
-- expressed by inbound references — kinotic_organization.ssoConfigId for the org's SSO,
-- kinotic_application.oidcConfigurationIds for application-level logins.
CREATE TABLE IF NOT EXISTS kinotic_oidc_configuration (
    id KEYWORD,
    organizationId KEYWORD,
    name KEYWORD,
    provider KEYWORD,
    clientId KEYWORD NOT INDEXED,
    secretNameRef KEYWORD NOT INDEXED,
    authority KEYWORD,
    authorizationUri KEYWORD NOT INDEXED,
    tokenUri KEYWORD NOT INDEXED,
    userInfoUri KEYWORD NOT INDEXED,
    userEmailsUri KEYWORD NOT INDEXED,
    scopes KEYWORD NOT INDEXED,
    audience KEYWORD NOT INDEXED,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- Kinotic-curated social IdP configs (Google, Microsoft Live, etc.) that power the
-- "Continue with X" buttons on org signup and email-first org login. Seeded via SQL
-- migration; not editable through the org admin UI.
CREATE TABLE IF NOT EXISTS kinotic_org_signup_oidc_configuration (
    id KEYWORD,
    name KEYWORD,
    provider KEYWORD,
    clientId KEYWORD NOT INDEXED,
    secretNameRef KEYWORD NOT INDEXED,
    authority KEYWORD,
    authorizationUri KEYWORD NOT INDEXED,
    tokenUri KEYWORD NOT INDEXED,
    userInfoUri KEYWORD NOT INDEXED,
    userEmailsUri KEYWORD NOT INDEXED,
    scopes KEYWORD NOT INDEXED,
    audience KEYWORD NOT INDEXED,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- Organization: orgs developing applications on the platform.
-- ssoConfigId points at the org's single OidcConfiguration used as its SSO provider for
-- org-level Kinotic login (null when the org has no SSO). All other OidcConfigurations
-- the org owns are referenced from the org's apps via kinotic_application.oidcConfigurationIds.
CREATE TABLE IF NOT EXISTS kinotic_organization (
    id KEYWORD,
    name KEYWORD,
    description TEXT,
    ssoConfigId KEYWORD NOT INDEXED,
    createdBy KEYWORD,
    storage OBJECT (azureSubscriptionId KEYWORD, azureAccountName KEYWORD, azureBlobEndpoint KEYWORD,
                    status OBJECT (type KEYWORD, message TEXT)),
    provisioningJobRunId KEYWORD,
    created DATE,
    updated DATE
);

-- Pending sign-ups (email-verification or OIDC) awaiting completion (PendingSignUp): the identity
-- to create plus authType. The organization name is collected at completion, not stored here.
CREATE TABLE IF NOT EXISTS kinotic_pending_signup (
    id KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE,
    email KEYWORD,
    displayName KEYWORD,
    authType KEYWORD,
    oidcSubject KEYWORD,
    oidcConfigId KEYWORD
);

-- Pending member invitations (PendingInvite) awaiting acceptance: the invitee's identity, the
-- scope they join (organizationId always set; applicationId only for app-member invites), and
-- inviter attribution for the email/accept page. No authType — the invitee chooses password or
-- OIDC at accept. Single-use: consumed and deleted when accepted, cancelled, or found expired.
CREATE TABLE IF NOT EXISTS kinotic_pending_invite (
    id KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE,
    email KEYWORD,
    displayName KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    invitedById KEYWORD,
    invitedByName KEYWORD
);

-- An application's customized invitation email (InviteEmailTemplate): Handlebars sources
-- replacing the built-in invitation template, at most one row per application.
CREATE TABLE IF NOT EXISTS kinotic_invite_email_template (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    subject TEXT,
    htmlBody TEXT,
    textBody TEXT,
    created DATE,
    updated DATE
);

-- OAuth 2.0 Device Authorization Grant (RFC 8628): pending CLI device-code login flows.
-- Short-lived (minutes); deleted once the CLI collects its tokens. deviceCodeHash is the
-- SHA-256 of the high-entropy device_code the CLI polls with — the plaintext is never stored.
CREATE TABLE IF NOT EXISTS kinotic_device_code_grant (
    id KEYWORD,
    deviceCodeHash KEYWORD,
    userCode KEYWORD,
    identityId KEYWORD,
    deviceName KEYWORD NOT INDEXED,
    created DATE,
    expiresAt DATE,
    lastPolledAt DATE,
    intervalSeconds INTEGER
);

-- Rotating refresh tokens for CLI sessions. tokenHash is the SHA-256 of the refresh token —
-- the plaintext lives only on the client. familyId groups a rotation lineage so presenting
-- an already-rotated token (reuse) can revoke the whole family.
-- audience is the surface access tokens minted from a lineage are valid for; rotation
-- preserves it, so an MCP host's lineage can never mint a published-services token or the
-- reverse.
CREATE TABLE IF NOT EXISTS kinotic_refresh_token (
    id KEYWORD,
    tokenHash KEYWORD,
    identityId KEYWORD,
    familyId KEYWORD,
    label KEYWORD NOT INDEXED,
    audience KEYWORD,
    created DATE,
    expiresAt DATE,
    lastUsedAt DATE,
    revoked BOOLEAN,
    replacedById KEYWORD NOT INDEXED
);

-- OAuth 2.1 authorization server (PKCE authorization-code grant) that MCP hosts drive to reach
-- POST /mcp. Clients are not stored: a client_id is a Client ID Metadata Document URL the
-- authorization server fetches and validates per request
-- (draft-ietf-oauth-client-id-metadata-document).
--
-- Authorization-code flows in progress: created by the authorize endpoint, bound to a user when
-- the consent page approves, and deleted when the code is exchanged. codeHash is the SHA-256 of
-- the authorization code — the plaintext is never stored.
CREATE TABLE IF NOT EXISTS kinotic_oauth_authorization_grant (
    id KEYWORD,
    clientId KEYWORD,
    clientName KEYWORD NOT INDEXED,
    redirectUri KEYWORD NOT INDEXED,
    codeChallenge KEYWORD NOT INDEXED,
    scope KEYWORD NOT INDEXED,
    resource KEYWORD NOT INDEXED,
    state KEYWORD NOT INDEXED,
    identityId KEYWORD,
    codeHash KEYWORD,
    created DATE,
    expiresAt DATE
);

-- Create the vm_node table for tracking VmManager nodes
CREATE TABLE IF NOT EXISTS kinotic_vm_node (
    id KEYWORD,
    name KEYWORD,
    hostname KEYWORD,
    status OBJECT (type KEYWORD, healthMessage TEXT),
    providerType KEYWORD,
    totalCpus INTEGER,
    totalMemoryMb INTEGER,
    totalDiskMb INTEGER,
    availableCpus INTEGER,
    availableMemoryMb INTEGER,
    availableDiskMb INTEGER,
    lastSeen DATE,
    workloadDataDir KEYWORD
);

-- Create the workload table for tracking deployed workloads.
-- organizationId/applicationId encode who the workload runs on behalf of (both null = platform
-- workload); the mapping is strict, so every Workload entity field must be declared here.
CREATE TABLE IF NOT EXISTS kinotic_workload (
    id KEYWORD,
    name KEYWORD,
    description TEXT,
    nodeId KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    image KEYWORD,
    vcpus INTEGER,
    memoryMb INTEGER,
    diskSizeMb INTEGER,
    network OBJECT (mode KEYWORD, allowedHosts KEYWORD),
    logPolicy OBJECT (maxSizeMb INTEGER, maxFiles INTEGER),
    telemetry BOOLEAN,
    detached BOOLEAN,
    autoRemove BOOLEAN,
    status KEYWORD,
    exitCode INTEGER,
    environment JSON NOT INDEXED,
    secrets JSON NOT INDEXED,
    portMappings OBJECT (hostPort INTEGER, guestPort INTEGER, protocol KEYWORD, hostIp KEYWORD),
    volumeMounts OBJECT (hostPath KEYWORD, guestPath KEYWORD, readOnly BOOLEAN, sizeLimitMb INTEGER),
    entrypoint KEYWORD NOT INDEXED,
    cmd KEYWORD NOT INDEXED,
    created DATE,
    updated DATE
);

-- Create the job_run table for the persistent history of grind job executions
CREATE TABLE IF NOT EXISTS kinotic_job_run (
    id KEYWORD,
    name KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    version KEYWORD,
    description TEXT,
    status KEYWORD,
    error TEXT,
    resumedFrom KEYWORD,
    nodeId KEYWORD,
    started DATE,
    finished DATE
);

-- Create the task_record table for the per-task history of a job run
CREATE TABLE IF NOT EXISTS kinotic_task_record (
    id KEYWORD,
    jobRunId KEYWORD,
    taskPath KEYWORD,
    description TEXT,
    status KEYWORD,
    storeType KEYWORD,
    dynamicTasks BOOLEAN,
    storedName KEYWORD,
    stateValueType KEYWORD,
    stateValue JSON NOT INDEXED,
    error TEXT,
    started DATE,
    finished DATE
);
