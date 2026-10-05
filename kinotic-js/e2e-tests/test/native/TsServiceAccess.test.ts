import {AuthzCheck, AuthzResource, BasicCredentialsResolver, Kinotic, KinoticSingleton, ServiceIdentifier, type IServiceProxy} from '@kinotic-ai/core'
import {type Resource, type Subject, SubjectKind} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {
    E2E_APP_TENANT as APP_TENANT,
    E2E_FIXTURE_PASSWORD,
    E2E_ORGANIZATION_ID as TEST_ORG_ID,
    appFixtureSubject,
    appServer,
    buildConnectOptions,
    connectAppClient,
    initKinoticClient,
    shutdownKinoticClient,
    until
} from '../TestHelpers.js'

// Fixed id: the app client logs in as app-<APP_ID>-<APP_TENANT>@test.local, an APPLICATION-scoped
// user that V3__e2e_app_fixtures seeds only for this applicationId.
const APP_ID = 'e2e-ts-service'
// the organization's APP_RUNTIME machine V3__e2e_app_fixtures seeds (clientSecret: kinotic), which hosts the service
const RUNTIME_CLIENT_ID = '00000000-0000-0000-0000-000000000013'
const ZONE = `app.${TEST_ORG_ID}.${APP_ID}`
const SERVICE = `${ZONE}~com.acme.reports.ReportService`

/**
 * The application's own service, hosted by the runtime's client rather than the organization's, so it is
 * registered on that client by hand instead of with Publish, which registers on the default client: its
 * contract is published as it registers, and the platform checks every request to it against the
 * application's store.
 */
@AuthzResource({value: 'report', parent: 'tenant', roles: [{id: 'report.generator', permissions: ['can_generate']}]})
class ReportService {

    async findReports(): Promise<string[]> {
        return ['quarterly']
    }

    @AuthzCheck({permission: 'can_generate', resource: 'tenant'})
    async generate(name: string): Promise<string> {
        return `generated ${name}`
    }
}

/**
 * Covers enforcement on a TypeScript service of an application: the runtime machine, granted the runtime role
 * on the application, publishes the service's contract as it connects; the application's user, granted nothing,
 * is refused the service; granted the viewer role of the service's type on its tenant it reads and is still
 * refused the function needing the declared permission; granted the declared role it is admitted. The
 * organization's administrator makes the grants.
 */
describe('Kinotic JS', () => {

    let runtime: KinoticSingleton
    let appKinotic: KinoticSingleton
    let reports: IServiceProxy
    let subject: Subject
    let runtimeGrantId: string | undefined
    const tenant: Resource = {type: 'tenant', id: APP_TENANT}

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('TsServiceAccess')
        await initKinoticClient()
        await Kinotic.applications.createApplicationIfNotExist(APP_ID, 'e2e fixture application for the TypeScript service test')
        // the runtime may publish the application's services, as a deployment's runtime machine may
        const runtimeGrant = await Kinotic.permissions.grant({kind: SubjectKind.USER, id: RUNTIME_CLIENT_ID}, 'application.runtime',
                                                             {type: 'application', id: APP_ID})
        runtimeGrantId = runtimeGrant.id

        runtime = new KinoticSingleton()
        await runtime.connect(buildConnectOptions(new BasicCredentialsResolver(RUNTIME_CLIENT_ID, E2E_FIXTURE_PASSWORD, TEST_ORG_ID), appServer()))
        // registering on the connected runtime publishes the contract at once
        runtime.serviceRegistry.register(new ServiceIdentifier('com.acme.reports', 'ReportService', ZONE), new ReportService())

        subject = await appFixtureSubject(APP_ID, APP_TENANT)
        appKinotic = await connectAppClient(APP_ID, APP_TENANT)
        reports = appKinotic.serviceProxy(SERVICE)
    }, 300000)

    afterAll(async () => {
        await appKinotic?.disconnect()
        await runtime?.disconnect()
        if (runtimeGrantId !== undefined) {
            await Kinotic.permissions.revoke({type: 'application', id: APP_ID}, runtimeGrantId).catch(() => undefined)
        }
        await Kinotic.applications.deleteById(APP_ID)
        await shutdownKinoticClient()
    })

    it('refuses an end user granted nothing once the contract is in the directory', async () => {
        // the store runs the service's type once its worker has written the model the contract implies
        await until(async () => (await Kinotic.applicationAccess.findRoles(APP_ID)).some(role => role.id === 'report.generator'))
        await expect(reports.invoke('findReports')).rejects.toThrowError(/report_can_view/)
    }, 60000)

    it('admits what the viewer role of the type reaches and refuses the declared permission', async () => {
        const grant = await Kinotic.applicationAccess.grant(APP_ID, subject, 'report.viewer', tenant)
        await until(async () => (await Kinotic.applicationAccess.explain(APP_ID, subject, 'report_can_view', tenant)).allowed)

        await expect(reports.invoke('findReports')).resolves.toEqual(['quarterly'])
        await expect(reports.invoke('generate', ['q3'])).rejects.toThrowError(/report_can_generate/)

        const generator = await Kinotic.applicationAccess.grant(APP_ID, subject, 'report.generator', tenant)
        await until(async () => (await Kinotic.applicationAccess.explain(APP_ID, subject, 'report_can_generate', tenant)).allowed)
        await expect(reports.invoke('generate', ['q3'])).resolves.toBe('generated q3')

        await Kinotic.applicationAccess.revoke(APP_ID, tenant, generator.id)
        await Kinotic.applicationAccess.revoke(APP_ID, tenant, grant.id)
        await until(async () => !(await Kinotic.applicationAccess.explain(APP_ID, subject, 'report_can_view', tenant)).allowed)
        await expect(reports.invoke('findReports')).rejects.toThrowError(/report_can_view/)
    }, 60000)
})
