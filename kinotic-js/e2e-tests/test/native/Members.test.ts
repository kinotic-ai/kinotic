import {Kinotic, Pageable} from '@kinotic-ai/core'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {E2E_ORG_USER_EMAIL, generateRandomString, initKinoticClient, managementServer, restBase, shutdownKinoticClient} from '../TestHelpers.js'

/**
 * An organization's members over STOMP, as one of them: the member list and search built from memberships, and a
 * person's life as a member from being added, with or without an invitation, through the pending state to removal.
 */
describe('Members', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('Members')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    function address(): string {
        return `e2e-member-${generateRandomString(8).toLowerCase()}@example.test`
    }

    async function pendingInvitationFor(email: string): Promise<boolean> {
        const pending = await Kinotic.members.findPendingInvites(null, Pageable.create(0, 100))
        return (pending.content ?? []).some(i => i.email === email)
    }

    it('lists the organization\'s members, the signed-in user among them', async () => {
        const page = await Kinotic.members.findMembers(Pageable.create(0, 50))
        const me = page.content?.find(m => m.email === E2E_ORG_USER_EMAIL)
        expect(me).toBeDefined()
        expect(me!.enabled).toBe(true)
        expect(me!.pending).toBe(false)
        expect(me!.id).toBeTruthy()
    })

    it('finds a member by name', async () => {
        const page = await Kinotic.members.searchMembers('Kinotic Test', Pageable.create(0, 10))
        expect(page.content?.some(m => m.email === E2E_ORG_USER_EMAIL)).toBe(true)
    })

    it('adds a person with an invitation, lists them as pending, sends the invitation again and removes them', async () => {
        const email = address()
        const added = await Kinotic.members.addOrganizationUser(email, 'Added Person', true)
        expect(added.id).toBeTruthy()
        expect(added.email).toBe(email)
        expect(added.pending).toBe(true)
        expect(added.enabled).toBe(true)

        const listed = await Kinotic.members.searchMembers(email, Pageable.create(0, 10))
        expect(listed.content?.find(m => m.id === added.id)?.pending).toBe(true)
        expect(await pendingInvitationFor(email)).toBe(true)

        await Kinotic.members.sendInvitation(email, null)

        // the member goes, and with them the invitation they never accepted
        await Kinotic.members.removeMember(added.id!)
        const afterRemoval = await Kinotic.members.searchMembers(email, Pageable.create(0, 10))
        expect(afterRemoval.content?.some(m => m.id === added.id)).toBe(false)
        expect(await pendingInvitationFor(email)).toBe(false)
    })

    it('adds a person without an invitation, since the organization has a provider of its own, and sends one later', async () => {
        const email = address()
        const added = await Kinotic.members.addOrganizationUser(email, 'Quiet Person', false)
        expect(added.id).toBeTruthy()
        expect(added.pending).toBe(true)
        expect(await pendingInvitationFor(email)).toBe(false)

        await Kinotic.members.sendInvitation(email, null)
        expect(await pendingInvitationFor(email)).toBe(true)

        await Kinotic.members.removeMember(added.id!)
        expect(await pendingInvitationFor(email)).toBe(false)
    })

    // the e2e stack sends no email, as a demonstration server does
    it('gives the inviter the link of an invitation no email delivers, which opens the invitation', async () => {
        const email = address()
        const added = await Kinotic.members.addOrganizationUser(email, null, true)

        const link = await Kinotic.members.findInvitationLink(email, null)
        expect(link).toMatch(/\/invite\/accept\?token=/)
        const details = await fetch(`${restBase(managementServer())}/api/auth/invite/details?token=${new URL(link!).searchParams.get('token')}`)
        expect(details.status).toBe(200)
        expect((await details.json()).email).toBe(email)

        await Kinotic.members.sendInvitation(email, null)
        expect(await Kinotic.members.findInvitationLink(email, null)).toBe(link)
        expect(await Kinotic.members.findInvitationLink(address(), null)).toBeNull()

        await Kinotic.members.removeMember(added.id!)
        expect(await Kinotic.members.findInvitationLink(email, null)).toBeNull()
    })

    it('refuses an address that is already a member, and a user of no application', async () => {
        await expect(Kinotic.members.addOrganizationUser(E2E_ORG_USER_EMAIL, null, true))
            .rejects.toThrow(/already exists in this organization/)
        await expect(Kinotic.members.sendInvitation(E2E_ORG_USER_EMAIL, null))
            .rejects.toThrow(/already signed in/)
        await expect(Kinotic.members.addApplicationUser({ email: address(), displayName: null, applicationId: '', tenantId: null, sendInvitation: true }))
            .rejects.toThrow(/applicationId/)
    })
})
