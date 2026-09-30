#!/usr/bin/env bun
// Registers a GitHub App for one developer's servers, reached through their ngrok domain, and
// writes the configuration the org and system servers need. See the contributing guide,
// "Local development environment".
//
//   bun dev-tools/github-app/dev-github-app.ts create --domain <you>.ngrok-free.dev [--name <app-name>] [--org <github-org>] [--force]
//   bun dev-tools/github-app/dev-github-app.ts sync-es [--organization-id kinotic-test] [--es http://localhost:9200]
//
// create runs GitHub's App manifest flow: a browser page posts the manifest below to GitHub,
// the developer confirms, GitHub redirects back here with a code, and the code is exchanged
// for the new App's id, key, webhook secret and OAuth credential. It writes
//   ~/.kinotic/dev-environment/kinotic-server-management/application.yml      the tunnel origin, the
//                                                                      sign-in secret and the App
//   ~/.kinotic/dev-environment/kinotic-server-system/application.yml   the App, with which the
//                                                                      system server mints fetch tokens
//   ~/.kinotic/dev-environment/github-app.json                         the full credential set, read by sync-es
// each server's file imported by its application-development.yml, run from the IDE or compose
// alike, then runs sync-es and restarts the compose servers that are running.
//
// sync-es restores what a fresh migration (docker compose down -v) loses and GitHub still has:
// it points the github-platform sign-in row, which the migration seeds with the kinotic-ai
// App's client id, at this App's, and links the App's installation to a Kinotic organization,
// as linking GitHub in the portal would. The App itself is registered once and reused.

import { createSign } from 'node:crypto'
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir, userInfo } from 'node:os'
import { dirname, join } from 'node:path'
import { parseArgs } from 'node:util'

const ENVIRONMENT_DIR = join(homedir(), '.kinotic', 'dev-environment')
// The servers that load management-api, each reading its own application.yml under its name,
// which is also its container's name in deployment/docker-compose/compose.kinotic-servers.yml
const MANAGEMENT_SERVER = 'kinotic-server-management'
const SYSTEM_SERVER = 'kinotic-server-system'
const APP_JSON = join(ENVIRONMENT_DIR, 'github-app.json')
// The OrgSignupOidcConfiguration row id and secretNameRef seeded in V1__init.sql
const SIGN_IN_CONFIG_ID = 'github-platform'
// Organization the development migration seeds and its kinotic@kinotic.local user belongs to
const DEFAULT_ORGANIZATION_ID = 'kinotic-test'

interface CreatedApp {
    id: number
    slug: string
    html_url: string
    client_id: string
    client_secret: string
    webhook_secret: string
    pem: string
    owner: { login: string }
}

const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
        domain: { type: 'string' },
        name: { type: 'string' },
        org: { type: 'string' },
        force: { type: 'boolean', default: false },
        es: { type: 'string', default: 'http://localhost:9200' },
        'organization-id': { type: 'string', default: DEFAULT_ORGANIZATION_ID },
    },
})

switch (positionals[0]) {
    case 'create':
        await create()
        break
    case 'sync-es':
        await syncEs(readApp())
        break
    default:
        fail('usage: dev-github-app.ts create --domain <you>.ngrok-free.dev [--name <app-name>] [--org <github-org>] [--force]\n'
             + '       dev-github-app.ts sync-es [--organization-id kinotic-test] [--es http://localhost:9200]')
}

async function create() {
    if (!values.domain) {
        fail('--domain is required: your ngrok static domain, e.g. --domain you.ngrok-free.dev')
    }
    if (existsSync(APP_JSON) && !values.force) {
        fail(`${APP_JSON} already holds App ${readApp().slug}. Delete that App on GitHub and pass --force to register another.`)
    }
    const origin = new URL(values.domain.includes('://') ? values.domain : `https://${values.domain}`).origin
    const name = values.name ?? defaultAppName()
    const state = crypto.randomUUID()

    const created = Promise.withResolvers<string>()
    const server = Bun.serve({
        hostname: '127.0.0.1',
        port: 0,
        fetch(req) {
            const url = new URL(req.url)
            if (url.pathname === '/') {
                return new Response(manifestPage(origin, name, `http://127.0.0.1:${server.port}/created`, state),
                                    { headers: { 'content-type': 'text/html' } })
            }
            if (url.pathname === '/created') {
                const code = url.searchParams.get('code')
                if (url.searchParams.get('state') !== state || !code) {
                    return new Response('State mismatch or missing code; run create again.', { status: 400 })
                }
                created.resolve(code)
                return new Response('GitHub App registered. Return to the terminal.')
            }
            return new Response('Not found', { status: 404 })
        },
    })

    // 127.0.0.1 rather than localhost, which Safari upgrades to https
    const start = `http://127.0.0.1:${server.port}/`
    console.log(`Opening ${start} — confirm the App on GitHub to continue.`)
    Bun.spawn([process.platform === 'darwin' ? 'open' : 'xdg-open', start])

    const code = await created.promise
    server.stop()

    const resp = await fetch(`https://api.github.com/app-manifests/${code}/conversions`, {
        method: 'POST',
        headers: { accept: 'application/vnd.github+json' },
    })
    if (!resp.ok) {
        fail(`Manifest conversion failed: ${resp.status} ${await resp.text()}`)
    }
    const app = (await resp.json()) as CreatedApp

    writeSecretFile(APP_JSON, JSON.stringify(app, null, 2) + '\n', 0o600)
    const written = [
        writeServerYml(MANAGEMENT_SERVER, managementServerYml(origin, app)),
        writeServerYml(SYSTEM_SERVER, systemServerYml(app)),
    ]

    console.log(`Registered ${app.html_url} (id ${app.id}) owned by ${app.owner.login}.`)
    console.log(`Wrote ${[...written, APP_JSON].join(', ')}.`)
    // The restart comes first: syncEs exits on a cluster the migration has not reached yet
    restartComposeServers()
    await syncEs(app)
}

function writeServerYml(server: string, content: string): string {
    const path = join(ENVIRONMENT_DIR, server, 'application.yml')
    // A compose server reads its application.yml through a bind mount as its image's own user,
    // which on Linux is another uid than yours; Docker Desktop maps ownership on macOS
    writeSecretFile(path, content, process.platform === 'linux' ? 0o644 : 0o600)
    return path
}

function restartComposeServers() {
    for (const server of [MANAGEMENT_SERVER, SYSTEM_SERVER]) {
        const running = Bun.spawnSync(['docker', 'ps', '--quiet', '--filter', `name=^${server}$`])
        if (running.success && running.stdout.toString().trim() !== '') {
            console.log(`Restarting the ${server} container to load the App.`)
            Bun.spawnSync(['docker', 'restart', server], { stdout: 'inherit', stderr: 'inherit' })
        } else {
            console.log(`Restart ${server} to load the App.`)
        }
    }
}

// GitHub App names are at most 34 characters of letters, digits and hyphens
function defaultAppName(): string {
    const user = userInfo().username.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '')
    return `kinotic-dev-${user}`.slice(0, 34).replace(/-+$/, '')
}

function manifestPage(origin: string, name: string, redirectUrl: string, state: string): string {
    const manifest = {
        name,
        url: origin,
        description: `Kinotic local development server at ${origin}`,
        public: false,
        hook_attributes: { url: `${origin}/api/github/webhook`, active: true },
        redirect_url: redirectUrl,
        // With request_oauth_on_install GitHub sends the post-install redirect to the first
        // callback URL, carrying the user-authorization code completeInstall verifies
        callback_urls: [
            `${origin}/github/install/callback`,
            `${origin}/api/auth/org/login/social/callback/${SIGN_IN_CONFIG_ID}`,
            `${origin}/api/auth/org/signup/social/callback/${SIGN_IN_CONFIG_ID}`,
            `${origin}/api/auth/invite/oidc/callback/${SIGN_IN_CONFIG_ID}`,
        ],
        request_oauth_on_install: true,
        // The installation-token scopes GitHubApiClient requests, and the sign-in email lookup.
        // The manifest form names the user email permission `emails`; the REST API's
        // `email_addresses` fails validation there.
        default_permissions: {
            administration: 'write',
            contents: 'write',
            metadata: 'read',
            emails: 'read',
        },
        default_events: ['push'],
    }
    const target = values.org
                   ? `https://github.com/organizations/${encodeURIComponent(values.org)}/settings/apps/new`
                   : 'https://github.com/settings/apps/new'
    const escaped = JSON.stringify(manifest)
                        .replaceAll('&', '&amp;')
                        .replaceAll('"', '&quot;')
                        .replaceAll('<', '&lt;')
                        .replaceAll('>', '&gt;')
    return `<!doctype html><html><body>
<form id="f" method="post" action="${target}?state=${state}">
  <input type="hidden" name="manifest" value="${escaped}">
  <button type="submit">Register ${name} on GitHub</button>
</form>
<script>document.getElementById('f').submit()</script>
</body></html>`
}

// The management server serves the portal, the API and the webhook at the tunnel's origin, and signs
// users in with the App's OAuth credential
function managementServerYml(origin: string, app: CreatedApp): string {
    return `# Written by dev-tools/github-app/dev-github-app.ts; imported by the management server's application-development.yml
# The github-platform sign-in row's OAuth client secret, resolved by EnvVarSecretReferenceResolver
KINOTIC_AKV_GITHUB_PLATFORM: ${JSON.stringify(app.client_secret)}
kinotic:
  managementServer:
    apiBaseUrl: ${origin}
    portalBaseUrl: ${origin}
  domain:
    email:
      linkBaseUrl: ${origin}
${githubYml(app)}`
}

// The system server keeps its own origins and mints deployments' fetch tokens as the App
function systemServerYml(app: CreatedApp): string {
    return `# Written by dev-tools/github-app/dev-github-app.ts; imported by the system server's application-development.yml
kinotic:
${githubYml(app)}`
}

function githubYml(app: CreatedApp): string {
    const pem = app.pem.trim().split('\n').map(line => `        ${line}`).join('\n')
    return `  managementApi:
    github:
      appId: "${app.id}"
      appSlug: ${app.slug}
      webhookSecret: ${JSON.stringify(app.webhook_secret)}
      appPrivateKey: |
${pem}
`
}

async function syncEs(app: CreatedApp) {
    try {
        await fetch(values.es)
    } catch {
        console.warn(`Elasticsearch is not reachable at ${values.es}. Once it is up and migrated, run:\n`
                     + '  bun dev-tools/github-app/dev-github-app.ts sync-es')
        return
    }
    await pointSignInRow(app.client_id)
    await linkInstallation(app, values['organization-id']!)
}

async function pointSignInRow(clientId: string) {
    const resp = await fetch(`${values.es}/kinotic_org_signup_oidc_configuration/_update/${SIGN_IN_CONFIG_ID}?refresh=true`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        // updated is part of the server's OAuth client cache key, so the new client id takes effect
        body: JSON.stringify({ doc: { clientId, updated: new Date().toISOString() } }),
    })
    if (!resp.ok) {
        fail(`Updating the ${SIGN_IN_CONFIG_ID} row failed: ${resp.status} ${await resp.text()}\n`
             + 'Run the migration first, then sync-es again.')
    }
    console.log(`The ${SIGN_IN_CONFIG_ID} sign-in row now uses client id ${clientId}.`)
}

// Writes the GitHubAppInstallation the portal's link flow would, keyed and routed as
// AbstractOrganizationScopedRepository stores it. The flow's ownership check is skipped: the
// App is private to its owner, so every installation it has is the developer's own.
async function linkInstallation(app: CreatedApp, organizationId: string) {
    const resp = await fetch('https://api.github.com/app/installations', {
        headers: { accept: 'application/vnd.github+json', authorization: `Bearer ${appJwt(app)}` },
    })
    if (!resp.ok) {
        fail(`Listing the installations of ${app.slug} failed: ${resp.status} ${await resp.text()}`)
    }
    const installations = (await resp.json()) as { id: number, account: { login: string, type: string }, suspended_at: string | null }[]
    if (installations.length === 0) {
        console.log(`${app.slug} is not installed yet; link GitHub from the portal to install it.`)
        return
    }
    if (installations.length > 1) {
        fail(`${app.slug} has ${installations.length} installations (${installations.map(i => i.account.login).join(', ')}); `
             + 'uninstall the ones you do not deploy from.')
    }
    const installation = installations[0]
    const now = new Date().toISOString()
    const id = String(installation.id)
    // An organization links one installation, as completeInstall enforces; a link left by an
    // App registered earlier would otherwise stay beside this one
    const cleared = await fetch(`${values.es}/kinotic_github_app_installation/_delete_by_query?routing=${organizationId}&refresh=true`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ query: { term: { organizationId } } }),
    })
    if (!cleared.ok) {
        fail(`Clearing the installation links of ${organizationId} failed: ${cleared.status} ${await cleared.text()}`)
    }
    const doc = await fetch(`${values.es}/kinotic_github_app_installation/_doc/${organizationId}-${id}?routing=${organizationId}&refresh=true`, {
        method: 'PUT',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
            id,
            organizationId,
            githubInstallationId: installation.id,
            accountLogin: installation.account.login,
            accountType: installation.account.type,
            suspendedAt: installation.suspended_at,
            created: now,
            updated: now,
        }),
    })
    if (!doc.ok) {
        fail(`Linking installation ${id} failed: ${doc.status} ${await doc.text()}`)
    }
    console.log(`Installation ${id} on ${installation.account.login} is linked to organization ${organizationId}.`)
}

// A GitHub App JWT: RS256 over the App id, valid for the ten minutes GitHub allows, issued a
// minute early to absorb clock drift
function appJwt(app: CreatedApp): string {
    const now = Math.floor(Date.now() / 1000)
    const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
    const unsigned = `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode({ iat: now - 60, exp: now + 540, iss: String(app.id) })}`
    return `${unsigned}.${createSign('RSA-SHA256').update(unsigned).sign(app.pem, 'base64url')}`
}

function readApp(): CreatedApp {
    if (!existsSync(APP_JSON)) {
        fail(`${APP_JSON} does not exist; run create first.`)
    }
    return JSON.parse(readFileSync(APP_JSON, 'utf8')) as CreatedApp
}

function writeSecretFile(path: string, content: string, mode: number) {
    try {
        mkdirSync(dirname(path), { recursive: true })
        writeFileSync(path, content, { mode })
        chmodSync(path, mode)
    } catch (error) {
        // Docker creates a bind mount's missing host directories as root when compose starts first
        if ((error as NodeJS.ErrnoException).code === 'EACCES') {
            fail(`${path} is not writable; if Docker created ${ENVIRONMENT_DIR}, run: sudo chown -R $USER ${ENVIRONMENT_DIR}`)
        }
        throw error
    }
}

function fail(message: string): never {
    console.error(message)
    process.exit(1)
}
