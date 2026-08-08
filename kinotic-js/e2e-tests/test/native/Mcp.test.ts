import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {McpError} from '@modelcontextprotocol/sdk/types.js'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {E2E_APP_TENANT,
        E2E_FIXTURE_PASSWORD,
        E2E_ORGANIZATION_ID,
        E2E_ORG_USER_EMAIL,
        E2E_SYSTEM_USER_EMAIL,
        appFixtureEmail,
        restBase} from '../TestHelpers.js'

// A tool name is the base-36 XXHash128 of '<qualified name>/<function>', minted server side by
// KinoticUtil.mcpToolName, so it says nothing on its own. Tools are named here by the title the server
// derives from the service and the function, and their minted names come from the listing.
const FIND_PROJECTS_BY_REPO = 'Project Service Find by GitHub Repo'
const CREATE_APPLICATION = 'Application Service Create Application If Not Exist'
// ProjectService inherits save from CrudService, which declares save(T entity), while the implementation it
// inherits — AbstractCrudService.save(T value) — names the same parameter 'value'.
const SAVE_PROJECT = 'Project Service Save'

/** applicationId of the APPLICATION-scope user seeded for this suite by V5__e2e_app_fixtures. */
const APP_ID = 'e2e-mcp'

async function connectMcpClient(authHeaders: Record<string, string>): Promise<Client> {
    const client = new Client({name: 'kinotic-e2e-tests', version: '1.0.0'})
    // /mcp is stateless: every request re-authenticates from these headers
    await client.connect(new StreamableHTTPClientTransport(new URL(`${restBase()}/mcp`),
                                                           {requestInit: {headers: authHeaders}}))
    return client
}

async function toolTitles(client: Client): Promise<string[]> {
    return (await client.listTools()).tools.map(tool => tool.title ?? '')
}

describe('Kinotic JS', () => {

    let systemClient: Client
    let organizationClient: Client
    let applicationClient: Client

    /** Title to minted name, read from the system listing once every expected tool is published. */
    const mintedNames = new Map<string, string>()

    function toolName(title: string): string {
        const name = mintedNames.get(title)
        if (name === undefined) {
            throw new Error(`No tool titled '${title}' in the listing`)
        }
        return name
    }

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('Mcp')

        systemClient = await connectMcpClient({clientId: E2E_SYSTEM_USER_EMAIL, clientSecret: E2E_FIXTURE_PASSWORD})
        organizationClient = await connectMcpClient({clientId: E2E_ORG_USER_EMAIL,
                                                     clientSecret: E2E_FIXTURE_PASSWORD,
                                                     organizationId: E2E_ORGANIZATION_ID})
        applicationClient = await connectMcpClient({clientId: appFixtureEmail(APP_ID, E2E_APP_TENANT),
                                                    clientSecret: E2E_FIXTURE_PASSWORD,
                                                    organizationId: E2E_ORGANIZATION_ID,
                                                    applicationId: APP_ID})

        // the directory publishes on server startup; wait for the listing to settle
        const expected = [FIND_PROJECTS_BY_REPO, CREATE_APPLICATION, SAVE_PROJECT]
        const deadline = Date.now() + 30000
        let titles: string[] = []
        while (!expected.every(title => titles.includes(title))) {
            if (Date.now() > deadline) {
                throw new Error(`Timed out waiting for ${expected} in the tool listing, got: ${titles}`)
            }
            titles = await toolTitles(systemClient)
            if (!expected.every(title => titles.includes(title))) {
                await new Promise(resolve => setTimeout(resolve, 1000))
            }
        }

        for (const tool of (await systemClient.listTools()).tools) {
            if (tool.title !== undefined) {
                mintedNames.set(tool.title, tool.name)
            }
        }
    }, 120000)

    afterAll(async () => {
        for (const client of [systemClient, organizationClient, applicationClient]) {
            await client?.close()
        }
    }, 60000)

    it('lists tools following the zone visibility matrix', async () => {
        expect(await toolTitles(systemClient), 'system sees every zone').toContain(CREATE_APPLICATION)

        expect(await toolTitles(organizationClient), 'org participants see os-api tools')
            .toContain(FIND_PROJECTS_BY_REPO)

        // every os-api tool's title leads with its service's half, so this catches the os-api tools this
        // suite never names as well as the three it does
        const leaked = (await toolTitles(applicationClient))
            .filter(title => title.startsWith('Project Service') || title.startsWith('Application Service'))
        expect(leaked, 'app participants never see os-api tools').toHaveLength(0)
    })

    it('serves tool metadata and schemas from the service contract', async () => {
        const findProjects = (await systemClient.listTools()).tools
            .find(tool => tool.name === toolName(FIND_PROJECTS_BY_REPO))

        expect(findProjects).toBeDefined()
        expect(findProjects!.description).toBe(
            "Looks up projects in the current participant's organization whose backing GitHub repo has the given "
            + 'owner/repo full name. Returns the empty list when no project in that organization is backed by the repo.')
        expect(findProjects!.annotations?.readOnlyHint).toBe(true)
        // served explicitly because MCP defaults it to true, which reads as a call that leaves the platform
        expect(findProjects!.annotations?.openWorldHint).toBe(false)
        // a name is a hash, so it carries nothing an LLM host would rewrite to a different name
        expect(findProjects!.name).toMatch(/^[0-9a-z]+$/)
        // schema property names come from the compiled parameter names
        expect(Object.keys(findProjects!.inputSchema.properties ?? {})).toContain('repoFullName')
    })

    it('dispatches tools/call through the rpc path', async () => {
        const result = await organizationClient.callTool({name: toolName(FIND_PROJECTS_BY_REPO),
                                                          arguments: {repoFullName: 'kinotic-ai/no-such-repo'}})
        expect(result.isError).toBeFalsy()
        const content = result.content as Array<{ type: string, text?: string }>
        expect(content[0].type).toBe('text')
        expect(content[0].text).toBe('[]')
    })

    it('rejects arguments matching no parameter as a tool error', async () => {
        const result = await organizationClient.callTool({name: toolName(FIND_PROJECTS_BY_REPO),
                                                          arguments: {repoFullName: 'a/b', bogus: 'x'}})
        expect(result.isError).toBe(true)
    })

    it('publishes and binds the interface parameter name where the implementation renames it', async () => {
        const save = (await systemClient.listTools()).tools.find(tool => tool.name === toolName(SAVE_PROJECT))
        expect(save, `'${SAVE_PROJECT}' is not exposed as a tool`).toBeDefined()

        // the interface's name, never the inherited implementation's 'value'
        expect(Object.keys(save!.inputSchema.properties ?? {})).toEqual(['entity'])

        // taking the argument name from the schema is what makes this a round trip: publishing a name the
        // service does not bind fails here, whichever name that is
        const [publishedName] = Object.keys(save!.inputSchema.properties ?? {})
        const result = await organizationClient.callTool({name: toolName(SAVE_PROJECT),
                                                          arguments: {[publishedName]: {}}})

        // an empty project still fails on its own business rules; only the binder's rejection is under test
        const content = result.content as Array<{ type: string, text?: string }>
        expect(content[0]?.text ?? '').not.toContain('matches no parameter')
    })

    it('refuses a call to a tool outside the caller zones', async () => {
        // resolution is scoped to the caller's zones, so the os-api tool does not exist for an app participant
        await expect(applicationClient.callTool({name: toolName(FIND_PROJECTS_BY_REPO),
                                                 arguments: {repoFullName: 'a/b'}}))
            .rejects.toThrowError(McpError)
    })

    it('paginates tools/list with cursors', async () => {
        const listing = await systemClient.listTools()
        expect(listing.nextCursor, 'everything fits one page so the listing carries no cursor').toBeUndefined()

        await expect(systemClient.listTools({cursor: 'not-a-cursor'})).rejects.toThrowError(McpError)
    })

    it('rejects unauthenticated requests', async () => {
        await expect(connectMcpClient({clientId: 'admin@kinotic.local', clientSecret: 'wrong-password'}))
            .rejects.toThrowError()
    })

})
