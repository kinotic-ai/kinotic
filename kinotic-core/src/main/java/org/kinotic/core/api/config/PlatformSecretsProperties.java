package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.nio.file.Path;

/**
 * Paths to platform-level secret files mounted into the pod by the secret provisioning
 * mechanism (Azure Key Vault CSI driver in production, a Kubernetes Secret volume locally).
 * <p>
 * Each file contains a {@link VersionedKeySet} serialized as JSON. Files are watched for
 * changes so secret rotation flows through without a pod restart.
 */
@Getter
@Setter
@Accessors(chain = true)
public class PlatformSecretsProperties {

    /**
     * Path to the JWT signing key set file (one {@link VersionedKeySet} JSON document).
     * Consumed by the Kinotic JWT issuer for signing STOMP-CONNECT tickets.
     */
    private Path jwtSigningKeysPath;

    /**
     * Path to the secret-storage master key set file (one {@link VersionedKeySet} JSON document).
     * Consumed by the secret name deriver for HKDF-based opaque naming.
     */
    private Path secretStorageMasterKeysPath;
}
