package org.kinotic.domain.api.model.iam;

/**
 * What kind of principal a {@link ParticipantIdentity} represents. Mirrors the concrete
 * subtype one-to-one — {@link #USER} for {@link UserParticipantIdentity}, {@link #DELEGATE}
 * for {@link DelegatingParticipantIdentity} — and is persisted as the document's polymorphic
 * discriminator, so it is also the value scope queries filter identities by kind with.
 */
public enum ParticipantIdentityType {
    USER,
    DELEGATE
}
