import { describe, expect, it } from 'bun:test'
import { AuthorizationException, type IParticipant } from '@kinotic-ai/core'
import { isApplicationParticipant, isOrganizationParticipant, requireParticipant } from '../src/api/model/security/ParticipantGuards'

// Participants as the server serializes them: the base fields plus the scope discriminator and ids
const ORGANIZATION = { type: 'organization', id: 'org-user', organizationId: 'acme-org', metadata: {}, roles: [] } as unknown as IParticipant
const APPLICATION = { type: 'application', id: 'app-user', organizationId: 'acme-org', applicationId: 'orders-app', metadata: {}, roles: [] } as unknown as IParticipant

describe('requireParticipant', () => {

    it('returns the participant narrowed to the scope the guard names', () => {
        const caller = requireParticipant(ORGANIZATION, isOrganizationParticipant)
        expect(caller.organizationId).toBe('acme-org')
        expect(requireParticipant(APPLICATION, isApplicationParticipant).applicationId).toBe('orders-app')
    })

    it('refuses a participant of another scope with only the generic message', () => {
        expect(() => requireParticipant(APPLICATION, isOrganizationParticipant)).toThrow(AuthorizationException)
        expect(() => requireParticipant(APPLICATION, isOrganizationParticipant)).toThrow('Access denied')
    })

    it('fails an invocation that carries no participant as a state error, not a refusal', () => {
        expect(() => requireParticipant(undefined, isOrganizationParticipant)).toThrow('No participant is bound to the invocation')
        expect(() => requireParticipant(undefined, isOrganizationParticipant)).not.toThrow(AuthorizationException)
    })
})
