/**
 * Identifies which scope layer a participant authenticated against. The server serializes this as
 * the {@code type} discriminator on a participant, and it is used to narrow a base
 * {@code IParticipant} to one of the scope-typed participant interfaces.
 */
export enum ParticipantType {
    SYSTEM = 'system',
    ORGANIZATION = 'organization',
    APPLICATION = 'application',
}
