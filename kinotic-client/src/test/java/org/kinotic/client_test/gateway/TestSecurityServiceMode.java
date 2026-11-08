package org.kinotic.client_test.gateway;

/**
 * How {@link TestGatewaySecurityService} answers a connection: from its credentials, by rejecting it whatever they
 * are, or by failing as a security service that cannot reach its identity store does.
 *
 * Created by Navíd Mitchell 🤪 on 11/5/26.
 */
public enum TestSecurityServiceMode {
    AUTHENTICATE,
    REJECT,
    FAIL
}
