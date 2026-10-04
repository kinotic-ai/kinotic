import {Client} from '@modelcontextprotocol/sdk/client/index.js'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {BasicCredentialsResolver, Kinotic, KinoticSingleton, type IServiceProxy} from '@kinotic-ai/core'
import {ManagementApiPlugin, Project} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {E2E_FIXTURE_PASSWORD,
        E2E_ORGANIZATION_ID,
        buildConnectOptions,
        initKinoticClient,
        managementServer,
        restBase,
        shutdownKinoticClient} from '../TestHelpers.js'

// the second kinotic-test organization user V2__kinotic_test_users seeds, who holds no grant until this suite makes one
const SALLY_ID = '00000000-0000-0000-0000-000000000004'
const SALLY_EMAIL = 'sally@kinotic.local'

const PERMISSION_SERVICE = 'management-api~org.kinotic.management.api.services.security.PermissionService'
const SAVE_PROJECT = 'Project Service Save'

async function connectMcpClient(authHeaders: Record<string, string>): Promise<Client> {
    const client = new Client({name: 'kinotic-e2e-tests', version: '1.0.0'})
    await client.connect(new StreamableHTTPClientTransport(new URL(`${restBase(managementServer())}/mcp`),
                                                           {requestInit: {headers: authHeaders}}))
    return client
}

/**
 * Covers enforcement of an organization's grants on the requests its members make: Sally, granted the project
 * editor role on project A, edits A and is refused on B, through the STOMP gateway and through the MCP endpoint,
 * and loses the access the moment the grant is revoked. The administrator grants and revokes through
 * PermissionService; Sally acts through her own connections.
 */
describe('Kinotic JS', () => {

    let permissions: IServiceProxy
    let sallyKinotic: KinoticSingleton
    let application: {id: string}
    let projectA: Project
    let projectB: Project
    let grantId: string | undefined

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('AccessControl')
        // the organization's administrator (kinotic@kinotic.local) creates the fixtures and makes the grant
        await initKinoticClient()
        permissions = Kinotic.serviceProxy(PERMISSION_SERVICE)
        application = await Kinotic.applications.createApplicationIfNotExist('e2e-access-control', 'e2e fixture application for the access control test')
        projectA = await Kinotic.projects.createProjectIfNotExist(new Project(null, application.id, 'access-control-a', 'Project A'))
        projectB = await Kinotic.projects.createProjectIfNotExist(new Project(null, application.id, 'access-control-b', 'Project B'))
        const grant = await permissions.invoke('grant', [{kind: 'USER', id: SALLY_ID}, 'project.editor', {type: 'project', id: projectA.id}])
        grantId = grant.id

        sallyKinotic = new KinoticSingleton()
        sallyKinotic.use(ManagementApiPlugin)
        await sallyKinotic.connect(buildConnectOptions(new BasicCredentialsResolver(SALLY_EMAIL, E2E_FIXTURE_PASSWORD, E2E_ORGANIZATION_ID)))
    }, 300000)

    afterAll(async () => {
        await sallyKinotic?.disconnect()
        if (grantId !== undefined) {
            await permissions.invoke('revoke', [{type: 'project', id: projectA.id}, grantId]).catch(() => undefined)
        }
        for (const project of [projectA, projectB]) {
            if (project?.id) {
                await Kinotic.projects.deleteById(project.id)
            }
        }
        await Kinotic.projects.syncIndex()
        if (application?.id) {
            await Kinotic.applications.deleteById(application.id)
        }
        await shutdownKinoticClient()
    }, 120000)

    it('lets an editor of one project edit it and refuses the other, through the gateway', async () => {
        const found = await sallyKinotic.projects.findById(projectA.id!)
        expect(found.id).toBe(projectA.id)
        const saved = await sallyKinotic.projects.save({...projectA, description: 'edited by Sally'})
        expect(saved.description).toBe('edited by Sally')

        await expect(sallyKinotic.projects.findById(projectB.id!)).rejects.toThrow(/Not authorized/)
        await expect(sallyKinotic.projects.save({...projectB, description: 'edited by Sally'})).rejects.toThrow(/Not authorized/)
        // an editor does not delete
        await expect(sallyKinotic.projects.deleteById(projectA.id!)).rejects.toThrow(/Not authorized/)
    })

    it('answers a refused tool call as the tool\'s error, through the MCP endpoint', async () => {
        const sallyMcp = await connectMcpClient({clientId: SALLY_EMAIL, clientSecret: E2E_FIXTURE_PASSWORD, organizationId: E2E_ORGANIZATION_ID})
        try {
            const save = (await sallyMcp.listTools()).tools.find(tool => tool.title === SAVE_PROJECT)
            expect(save, `'${SAVE_PROJECT}' is not exposed as a tool`).toBeDefined()
            const [argumentName] = Object.keys(save!.inputSchema.properties ?? {})

            const refused = await sallyMcp.callTool({name: save!.name, arguments: {[argumentName]: {...projectB, description: 'edited by Sally'}}})
            expect(refused.isError).toBe(true)
            expect((refused.content as Array<{text?: string}>)[0]?.text ?? '').toContain('Not authorized')

            const allowed = await sallyMcp.callTool({name: save!.name, arguments: {[argumentName]: {...projectA, description: 'edited by Sally over MCP'}}})
            expect(allowed.isError).toBeFalsy()
        } finally {
            await sallyMcp.close()
        }
    })

    it('stops the access the moment the grant is revoked', async () => {
        await permissions.invoke('revoke', [{type: 'project', id: projectA.id}, grantId])
        grantId = undefined

        await expect(sallyKinotic.projects.save({...projectA, description: 'edited after revocation'})).rejects.toThrow(/Not authorized/)
    })
})
