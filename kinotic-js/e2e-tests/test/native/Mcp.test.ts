import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {McpError} from '@modelcontextprotocol/sdk/types.js'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'

// Tool names are minted server side: the service's qualified name with '~' as '.', then the function name
const FIND_PROJECTS_BY_REPO = 'os-api.org.kinotic.os.api.services.ProjectService.findByRepoFullName'
const CREATE_APPLICATION = 'os-api.org.kinotic.os.api.services.ApplicationService.createApplicationIfNotExist'

/** APPLICATION-scope fixture application seeded for this suite by V5__e2e_app_fixtures. */
const APP_ID = 'e2e-mcp'
const ORGANIZATION_ID = 'kinotic-test'

async function connectMcpClient(authHeaders: Record<string, string>): Promise<Client> {
    const host = inject('KINOTIC_HOST')
    const port = inject('KINOTIC_PORT')
    const client = new Client({name: 'kinotic-e2e-tests', version: '1.0.0'})
    // /mcp is stateless: every request re-authenticates from these headers
    await client.connect(new StreamableHTTPClientTransport(new URL(`http://${host}:${port}/mcp`),
                                                           {requestInit: {headers: authHeaders}}))
    return client
}

async function toolNames(client: Client): Promise<string[]> {
    return (await client.listTools()).tools.map(tool => tool.name)
}

describe('Kinotic JS', () => {

    let systemClient: Client
    let organizationClient: Client
    let applicationClient: Client

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('Mcp')

        systemClient = await connectMcpClient({clientId: 'admin@kinotic.local', clientSecret: 'kinotic'})
        organizationClient = await connectMcpClient({clientId: 'kinotic@kinotic.local',
                                                     clientSecret: 'kinotic',
                                                     organizationId: ORGANIZATION_ID})
        applicationClient = await connectMcpClient({clientId: `app-${APP_ID}-kinotic@test.local`,
                                                    clientSecret: 'kinotic',
                                                    organizationId: ORGANIZATION_ID,
                                                    applicationId: APP_ID})

        // the directory publishes on server startup; wait for the listing to settle
        const deadline = Date.now() + 30000
        while (!(await toolNames(systemClient)).includes(FIND_PROJECTS_BY_REPO)) {
            if (Date.now() > deadline) {
                throw new Error(`Timed out waiting for ${FIND_PROJECTS_BY_REPO} to appear in the tool listing`)
            }
            await new Promise(resolve => setTimeout(resolve, 1000))
        }
    }, 120000)

    afterAll(async () => {
        for (const client of [systemClient, organizationClient, applicationClient]) {
            await client?.close()
        }
    }, 60000)

    it('lists tools following the zone visibility matrix', async () => {
        expect(await toolNames(systemClient), 'system sees every zone').toContain(CREATE_APPLICATION)

        expect(await toolNames(organizationClient), 'org participants see os-api tools')
            .toContain(FIND_PROJECTS_BY_REPO)

        const applicationTools = await toolNames(applicationClient)
        expect(applicationTools.filter(name => name.startsWith('os-api.')),
               'app participants never see os-api tools').toEqual([])
    })

    it('serves tool metadata and schemas from the service contract', async () => {
        const tools = (await systemClient.listTools()).tools
        const findProjects = tools.find(tool => tool.name === FIND_PROJECTS_BY_REPO)

        expect(findProjects).toBeDefined()
        expect(findProjects!.description).toBe(
            "Looks up projects in the current participant's organization whose backing GitHub repo has the given "
            + 'owner/repo full name. Returns the empty list when no project in that organization is backed by the repo.')
        expect(findProjects!.title).toBe('Find Projects by GitHub Repo')
        expect(findProjects!.annotations?.readOnlyHint).toBe(true)
        // parameter names are not retained in the class file, so a parameter is argN unless the
        // service annotates it with @Name
        expect(Object.keys(findProjects!.inputSchema.properties ?? {})).toContain('arg0')
    })

    it('dispatches tools/call through the rpc path', async () => {
        const result = await organizationClient.callTool({name: FIND_PROJECTS_BY_REPO,
                                                          arguments: {arg0: 'kinotic-ai/no-such-repo'}})
        expect(result.isError).toBeFalsy()
        const content = result.content as Array<{ type: string, text?: string }>
        expect(content[0].type).toBe('text')
        expect(content[0].text).toBe('[]')
    })

    it('rejects arguments matching no parameter as a tool error', async () => {
        const result = await organizationClient.callTool({name: FIND_PROJECTS_BY_REPO,
                                                          arguments: {arg0: 'a/b', bogus: 'x'}})
        expect(result.isError).toBe(true)
    })

    it('refuses a call to a tool outside the caller zones', async () => {
        // resolution is scoped to the caller's zones, so the os-api tool does not exist for an app participant
        await expect(applicationClient.callTool({name: FIND_PROJECTS_BY_REPO,
                                                 arguments: {arg0: 'a/b'}}))
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
