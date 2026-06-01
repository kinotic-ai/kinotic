package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A {@link PendingSignUp} for the email/password flow. The user supplies their org details,
 * email, and display name up front; the email is verified by clicking the link carrying
 * {@link #getVerificationToken()}. The chosen password is collected only at completion and is
 * never stored on this record.
 */
@Getter
@Setter
@NoArgsConstructor
public final class LocalPendingSignUp extends PendingSignUp {

    /** Name for the Organization to create on completion. */
    private String orgName;

    /** Description for the Organization to create on completion (may be null). */
    private String orgDescription;
}
