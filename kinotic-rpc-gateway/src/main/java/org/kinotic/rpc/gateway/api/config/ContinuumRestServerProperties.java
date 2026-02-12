

package org.kinotic.rpc.gateway.api.config;

/**
 *
 * Created by navid on 12/19/19
 */
public class ContinuumRestServerProperties {

    private int port = ContinuumGatewayProperties.DEFAULT_REST_PORT;
    private String restPath = ContinuumGatewayProperties.DEFAULT_REST_PATH;
    private long bodyLimitSize = ContinuumGatewayProperties.DEFAULT_REST_BODY_LIMIT_SIZE;

    public ContinuumRestServerProperties() {
    }

    public int getPort() {
        return port;
    }

    public ContinuumRestServerProperties setPort(int port) {
        this.port = port;
        return this;
    }

    public String getRestPath() {
        return restPath;
    }

    public ContinuumRestServerProperties setRestPath(String restPath) {
        this.restPath = restPath;
        return this;
    }

    public long getBodyLimitSize() {
        return bodyLimitSize;
    }

    public ContinuumRestServerProperties setBodyLimitSize(long bodyLimitSize) {
        this.bodyLimitSize = bodyLimitSize;
        return this;
    }
}
