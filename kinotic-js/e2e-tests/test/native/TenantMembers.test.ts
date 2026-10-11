import {Kinotic, KinoticSingleton, Pageable} from '@kinotic-ai/core'
import {OnboardingMechanism, SubjectKind, type Subject} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {
    E2E_APP_TENANT as APP_TENANT,
    appFixtureSubject,
    initKinoticAppClient,
    initKinoticClient,
    shutdownKinoticClient,
    until
} from '../TestHelpers.js'

// Fixed id: the app client logs in as app-<APP_ID>-<APP_TENANT>@test.local, an APPLICATION-scoped user that
// V3__e2e_app_fixtures seeds only for this applicationId, with the tenant record its tenant service answers with.
const APP_ID = 'e2e-tenant-members'
const FIRST_PAGE = Pageable.create(0, 10)

/**
 * Covers the tenant services from an application's own client: the fixture user, granted the tenant admin role
 * on its tenant, reads and renames the tenant, lists the tenant's members, invites a colleague into it and
 * cancels the invitation, and grants a role on the tenant and revokes it.
 */
describe('Kinotic JS', () => {

    let appKinotic: KinoticSingleton
    let subject: Subject

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('TenantMembers')
        await initKinoticClient()
        await Kinotic.applications.createApplicationIfNotExist(APP_ID, 'e2e fixture application for the tenant members test',
                                                               [OnboardingMechanism.TENANT_INVITE])
        subject = await appFixtureSubject(APP_ID, APP_TENANT)
        appKinotic = await initKinoticAppClient(APP_ID, APP_TENANT)
    })

    afterAll(async () => {
        await appKinotic?.disconnect()
        await shutdownKinoticClient()
    })

    it('reads and renames the tenant', async () => {
        // the store answers for the tenant admin grant within moments of its making
        await until(async () => (await appKinotic.tenant.getTenant()).tenantId === APP_TENANT)
        const renamed = await appKinotic.tenant.rename('Kinotic e2e')
        expect(renamed.name).toBe('Kinotic e2e')
        expect((await appKinotic.tenant.getTenant()).name).toBe('Kinotic e2e')
    })

    it("lists the tenant's members, invites into it and cancels the invitation", async () => {
        const members = await appKinotic.tenantMembers.findMembers(FIRST_PAGE)
        expect(members.content?.map(member => member.id)).toContain(subject.id)

        const invitation = await appKinotic.tenantMembers.inviteMember(`invitee-${Date.now()}@kinotic.test`, 'Invitee')
        expect(invitation.tenantId).toBe(APP_TENANT)
        const pending = await appKinotic.tenantMembers.findPendingInvites(FIRST_PAGE)
        expect(pending.content?.map(invite => invite.id)).toContain(invitation.id)

        await appKinotic.tenantMembers.cancelInvite(invitation.id!)
        const remaining = await appKinotic.tenantMembers.findPendingInvites(FIRST_PAGE)
        expect(remaining.content?.map(invite => invite.id)).not.toContain(invitation.id)
    })

    it('grants a role on the tenant and revokes it', async () => {
        const roles = await appKinotic.tenantMembers.findRoles()
        expect(roles.map(role => role.id)).toContain('tenant.viewer')
        expect(roles.some(role => role.id?.startsWith('application.'))).toBe(false)

        const grant = await appKinotic.tenantMembers.grant({kind: SubjectKind.USER, id: subject.id}, 'tenant.viewer')
        expect((await appKinotic.tenantMembers.findGrants()).map(made => made.id)).toContain(grant.id)

        await appKinotic.tenantMembers.revoke(grant.id)
        expect((await appKinotic.tenantMembers.findGrants()).map(made => made.id)).not.toContain(grant.id)
    })
})
