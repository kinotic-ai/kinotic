package org.kinotic.os.api.model.iam;

/**
 * The three IAM scope layers. Each layer maintains completely separate user pools.
 * SYSTEM is for platform operators, ORGANIZATION is for development teams,
 * and APPLICATION is for end-users consuming deployed applications.
 */
public enum AuthScope {
    SYSTEM,
    ORGANIZATION,
    APPLICATION
}
