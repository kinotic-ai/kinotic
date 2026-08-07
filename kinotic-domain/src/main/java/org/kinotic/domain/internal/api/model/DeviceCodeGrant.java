package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A pending OAuth 2.0 Device Authorization Grant (RFC 8628). Created when a CLI starts the
 * device flow, polled by the CLI until a browser-authenticated user approves it, then
 * consumed (deleted) once the CLI collects its tokens.
 * <p>
 * Internal-only — never published. Only ever holds a SHA-256 hash of the device code, never
 * the plaintext.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class DeviceCodeGrant implements Identifiable<String> {

    private String id;

    /** SHA-256 hash of the high-entropy {@code device_code} the CLI polls with. */
    private String deviceCodeHash;

    /** Low-entropy, human-typed code shown by the CLI and entered in the browser. */
    private String userCode;

    /** Optional device name the CLI supplied; becomes the label of the issued token family. */
    private String deviceName;

    /**
     * Id of the {@link org.kinotic.domain.api.model.security.ParticipantIdentity} that approved the grant,
     * or {@code null} while the grant is still pending approval.
     */
    private String identityId;

    private Date created;

    /** Hard expiry after which the grant can no longer be polled or approved. */
    private Date expiresAt;

    /** When the CLI last polled; used to enforce the minimum poll interval. {@code null} until first poll. */
    private Date lastPolledAt;

    /** Minimum seconds the CLI must wait between polls. */
    private int intervalSeconds;
}
