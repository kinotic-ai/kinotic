-- Pending member invitations (PendingInvite) awaiting acceptance: the invitee's identity, the
-- scope they join (organizationId always set; applicationId only for app-member invites), and
-- inviter attribution for the email/accept page. No authType — the invitee chooses password or
-- OIDC at accept. Single-use: consumed and deleted when accepted, cancelled, or found expired.
CREATE TABLE IF NOT EXISTS kinotic_pending_invite (
    id KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE,
    email KEYWORD,
    displayName KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    invitedById KEYWORD,
    invitedByName KEYWORD
);
