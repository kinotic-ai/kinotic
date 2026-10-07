import {BasicCredentialsResolver, type IServiceProxy, Kinotic, KinoticSingleton, Pageable} from '@kinotic-ai/core'
import {ManagementApiPlugin, type Resource, type Subject, SubjectKind} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import '../generated/ServiceDefinitions.js'
import {ReportService} from '../services/ReportService.js'
import {
    E2E_APP_TENANT as APP_TENANT,
    E2E_FIXTURE_PASSWORD,
    E2E_ORG_USER_EMAIL,
    E2E_ORGANIZATION_ID as TEST_ORG_ID,
    appFixtureEmail,
    appServer,
    buildConnectOptions,
    connectAppClient,
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
 * Covers enforcement on a TypeScript service of an application, hosted as a deployed runtime hosts it: the
 * global client, connected as the runtime machine granted the runtime role on the application, registers the
 * service in the directory with its generated definition as it connects; the application's user, granted
 * nothing, is refused the service; granted the viewer role of the service's type on its tenant it reads and is
 * still refused the function needing the declared permission; granted the declared role it is admitted. The
 * organization's administrator, on a client of its own, makes the grants.
 */
describe('Kinotic JS', () => {

    const admin = new KinoticSingleton()
    let appKinotic: KinoticSingleton
    let reports: IServiceProxy
    let subject: Subject
    let runtimeGrantId: string | undefined
    const tenant: Resource = {type: 'tenant', id: APP_TENANT}

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('TsServiceAccess')
        admin.use(ManagementApiPlugin)
        await admin.connect(buildConnectOptions(new BasicCredentialsResolver(E2E_ORG_USER_EMAIL, E2E_FIXTURE_PASSWORD, TEST_ORG_ID)))
        await admin.applications.createApplicationIfNotExist(APP_ID, 'e2e fixture application for the TypeScript service test')
        // the runtime may register the application's services, as a deployment's runtime machine may
        const runtimeGrant = await admin.permissions.grant({kind: SubjectKind.USER, id: RUNTIME_CLIENT_ID}, 'application.runtime',
                                                           {type: 'application', id: APP_ID})
        runtimeGrantId = runtimeGrant.id

        // the service registers under the application's zone as it is constructed, and in the directory as the
        // runtime connects
        Kinotic.zonePrefix = ZONE
        new ReportService()
        await Kinotic.connect(buildConnectOptions(new BasicCredentialsResolver(RUNTIME_CLIENT_ID, E2E_FIXTURE_PASSWORD, TEST_ORG_ID), appServer()))

        const email = appFixtureEmail(APP_ID, APP_TENANT)
        const user = (await admin.members.findMembers(APP_ID, Pageable.create(0, 100))).content?.find(member => member.email === email)
        if (!user?.id) {
            throw new Error(`No user ${email} is seeded for application ${APP_ID}`)
        }
        subject = {kind: SubjectKind.USER, id: user.id}
        appKinotic = await connectAppClient(APP_ID, APP_TENANT)
        reports = appKinotic.serviceProxy(SERVICE)
    }, 300000)

    afterAll(async () => {
        await appKinotic?.disconnect()
        await Kinotic.disconnect()
        Kinotic.zonePrefix = null
        if (runtimeGrantId !== undefined) {
            await admin.permissions.revoke({type: 'application', id: APP_ID}, runtimeGrantId).catch(() => undefined)
        }
        await admin.applications.deleteById(APP_ID)
        await admin.disconnect()
    })

    it('refuses an end user granted nothing once the service is in the directory', async () => {
        // the store runs the service's type once its worker has written the model the definition implies
        await until(async () => (await admin.applicationAccess.findRoles(APP_ID)).some(role => role.id === 'report.generator'))
        await expect(reports.invoke('findReports')).rejects.toThrowError(/report_can_view/)
    }, 60000)

    it('admits what the viewer role of the type reaches and refuses the declared permission', async () => {
        const grant = await admin.applicationAccess.grant(APP_ID, subject, 'report.viewer', tenant)
        await until(async () => (await admin.applicationAccess.explain(APP_ID, subject, 'report_can_view', tenant)).allowed)

        await expect(reports.invoke('findReports')).resolves.toEqual(['quarterly'])
        await expect(reports.invoke('generate', ['q3'])).rejects.toThrowError(/report_can_generate/)

        const generator = await admin.applicationAccess.grant(APP_ID, subject, 'report.generator', tenant)
        await until(async () => (await admin.applicationAccess.explain(APP_ID, subject, 'report_can_generate', tenant)).allowed)
        await expect(reports.invoke('generate', ['q3'])).resolves.toBe('generated q3')

        await admin.applicationAccess.revoke(APP_ID, tenant, generator.id)
        await admin.applicationAccess.revoke(APP_ID, tenant, grant.id)
        await until(async () => !(await admin.applicationAccess.explain(APP_ID, subject, 'report_can_view', tenant)).allowed)
        await expect(reports.invoke('findReports')).rejects.toThrowError(/report_can_view/)
    }, 60000)
})
