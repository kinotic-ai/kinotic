#!/usr/bin/env bun
// Registers a GitHub App for one developer's kinotic-server, reached through their ngrok
// domain, and writes the configuration that server needs. See the contributing guide,
// "Local development environment".
//
//   bun dev-tools/github-app/dev-github-app.ts create --domain <you>.ngrok-free.dev [--name <app-name>] [--org <github-org>] [--force]
//   bun dev-tools/github-app/dev-github-app.ts sync-es [--es http://localhost:9200]
//
// create runs GitHub's App manifest flow: a browser page posts the manifest below to GitHub,
// the developer confirms, GitHub redirects back here with a code, and the code is exchanged
// for the new App's id, key, webhook secret and OAuth credential. It writes
//   ~/.kinotic/dev-environment/application.yml   imported by application-development.yml, in the
//                                                IDE server and the compose server alike
//   ~/.kinotic/dev-environment/github-app.json   the full credential set, read by sync-es
// then runs sync-es and restarts the compose kinotic-server container when it is running.
//
// sync-es points the github-platform sign-in row in local Elasticsearch at the App's client
// id. The migration seeds that row with the kinotic-ai App's client id, so this runs again
// after every fresh migration (docker compose down -v).

import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir, userInfo } from 'node:os'
import { join } from 'node:path'
import { parseArgs } from 'node:util'

const ENVIRONMENT_DIR = join(homedir(), '.kinotic', 'dev-environment')
const ENVIRONMENT_YML = join(ENVIRONMENT_DIR, 'application.yml')
const APP_JSON = join(ENVIRONMENT_DIR, 'github-app.json')
// The OrgSignupOidcConfiguration row id and secretNameRef seeded in V1__init.sql
const SIGN_IN_CONFIG_ID = 'github-platform'
// Container name in deployment/docker-compose/compose.kinotic-server.yml
const COMPOSE_SERVER = 'kinotic-server'

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
    },
})

switch (positionals[0]) {
    case 'create':
        await create()
        break
    case 'sync-es':
        await syncEs(readApp().client_id)
        break
    default:
        fail('usage: dev-github-app.ts create --domain <you>.ngrok-free.dev [--name <app-name>] [--org <github-org>] [--force]\n'
             + '       dev-github-app.ts sync-es [--es http://localhost:9200]')
}

async function create() {
    if (!values.domain) {
        fail('--domain is required: your ngrok static domain, e.g. --domain you.ngrok-free.dev')
    }
    if (existsSync(APP_JSON) && !values.force) {
        fail(`${APP_JSON} already holds App ${readApp().slug}. Delete that App on GitHub and pass --force to register another.`)
    }
    const origin = new URL(values.domain.includes('://') ? values.domain : `https://${values.domain}`).origin
    const name = values.name ?? `kinotic-dev-${userInfo().username}`
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

    mkdirSync(ENVIRONMENT_DIR, { recursive: true })
    writeSecretFile(APP_JSON, JSON.stringify(app, null, 2) + '\n')
    writeSecretFile(ENVIRONMENT_YML, environmentYml(origin, app))

    console.log(`Registered ${app.html_url} (id ${app.id}) owned by ${app.owner.login}.`)
    console.log(`Wrote ${ENVIRONMENT_YML} and ${APP_JSON}.`)
    await syncEs(app.client_id)
    restartComposeServer()
}

function restartComposeServer() {
    const running = Bun.spawnSync(['docker', 'ps', '--quiet', '--filter', `name=^${COMPOSE_SERVER}$`])
    if (running.success && running.stdout.toString().trim() !== '') {
        console.log(`Restarting the ${COMPOSE_SERVER} container to load the App.`)
        Bun.spawnSync(['docker', 'restart', COMPOSE_SERVER], { stdout: 'inherit', stderr: 'inherit' })
    } else {
        console.log('Restart kinotic-server to load the App.')
    }
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

function environmentYml(origin: string, app: CreatedApp): string {
    const pem = app.pem.trim().split('\n').map(line => `        ${line}`).join('\n')
    return `# Written by dev-tools/github-app/dev-github-app.ts; imported by application-development.yml
# The github-platform sign-in row's OAuth client secret, resolved by EnvVarSecretReferenceResolver
KINOTIC_AKV_GITHUB_PLATFORM: ${JSON.stringify(app.client_secret)}
kinotic:
  domain:
    appBaseUrl: ${origin}
    apiBaseUrl: ${origin}
  managementApi:
    github:
      appId: "${app.id}"
      appSlug: ${app.slug}
      webhookSecret: ${JSON.stringify(app.webhook_secret)}
      appPrivateKey: |
${pem}
`
}

async function syncEs(clientId: string) {
    const url = `${values.es}/kinotic_org_signup_oidc_configuration/_update/${SIGN_IN_CONFIG_ID}?refresh=true`
    let resp: Response
    try {
        resp = await fetch(url, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ doc: { clientId } }),
        })
    } catch {
        console.warn(`Elasticsearch is not reachable at ${values.es}. Once it is up and migrated, run:\n`
                     + '  bun dev-tools/github-app/dev-github-app.ts sync-es')
        return
    }
    if (!resp.ok) {
        fail(`Updating the ${SIGN_IN_CONFIG_ID} row failed: ${resp.status} ${await resp.text()}\n`
             + 'Run the migration first, then sync-es again.')
    }
    console.log(`The ${SIGN_IN_CONFIG_ID} sign-in row now uses client id ${clientId}.`)
}

function readApp(): CreatedApp {
    if (!existsSync(APP_JSON)) {
        fail(`${APP_JSON} does not exist; run create first.`)
    }
    return JSON.parse(readFileSync(APP_JSON, 'utf8')) as CreatedApp
}

function writeSecretFile(path: string, content: string) {
    writeFileSync(path, content, { mode: 0o600 })
    chmodSync(path, 0o600)
}

function fail(message: string): never {
    console.error(message)
    process.exit(1)
}
