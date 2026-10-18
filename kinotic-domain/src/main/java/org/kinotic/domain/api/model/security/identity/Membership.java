package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.domain.api.model.Organization;

import java.util.Date;

/**
 * An account's membership of an organization, or of the platform itself: the account is a
 * {@link UserParticipantIdentity} holding no scope of its own, and each membership is one scope the person
 * may sign in to. A person has one account and any number of memberships, so one email, one password and
 * one GitHub binding reach every organization the person belongs to. A membership can be disabled or
 * removed by the organization without touching the account or the person's other memberships.
 *
 * <p>The id is derived from the identity and the organization ({@link #idOf}), so a membership is read by a
 * get and exists at most once per account and scope.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Membership implements Identifiable<String> {

    /** The id of the platform itself as a membership's organization, since an {@link Organization}'s id is a slug; no organization may take it. */
    public static final String PLATFORM = "system";

    private String id;

    /** The account the membership belongs to. */
    private String identityId;

    /**
     * The organization the account is a member of, or {@code null} for a membership of the platform: a
     * platform operator, who signs in to the system console.
     */
    private String organizationId;

    /** Whether the account may sign in to this scope; a disabled membership refuses it while the account itself stays usable elsewhere. */
    private boolean enabled;

    private Date created;

    private Date updated;

    /** The id of the membership of account {@code identityId} in organization {@code organizationId}, {@code null} for the platform. */
    public static String idOf(String identityId, String organizationId) {
        return identityId + ":" + (organizationId != null ? organizationId : PLATFORM);
    }
}
