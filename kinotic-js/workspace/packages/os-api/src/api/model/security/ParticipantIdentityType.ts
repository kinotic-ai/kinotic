/**
 * What kind of principal a {@link ParticipantIdentity} represents; the persisted polymorphic
 * discriminator of the document.
 */
export enum ParticipantIdentityType {
    USER = 'USER',
    DELEGATE = 'DELEGATE',
    MACHINE = 'MACHINE'
}
