

package org.kinotic.continuum.internal.core.api.support;

/**
 *
 * Created by navid on 10/17/19
 */
public class DefaultClusterTestService implements ClusterTestService {

    private final String data;

    public DefaultClusterTestService(String data) {
        this.data = data;
    }

    @Override
    public Long getFreeMemory() {
        return 428L;
    }

    @Override
    public String getData() {
        return data;
    }
}
