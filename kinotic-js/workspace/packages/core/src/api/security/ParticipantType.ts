/**
 * Identifies which scope layer a participant authenticated against. The server serializes this
 * as the {@code type} discriminator on a participant, and it is used to narrow an
 * {@link IParticipant} to its concrete subtype.
 */
export enum ParticipantType {
    SYSTEM = 'system',
    ORGANIZATION = 'organization',
    APPLICATION = 'application',
}
