

package org.kinotic.rpc.internal.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 *
 * Created by Navid Mitchell 🤪 on 10/24/19
 */
@Component
@ConfigurationProperties(prefix = "continuum")
@Getter
@Setter
@Accessors(chain = true)
public class DefaultContinuumProperties implements ContinuumProperties {

    public static long DEFAULT_SESSION_TIMEOUT = 1000 * 60 * 30;

    private boolean debug = false;
    private boolean disableClustering = false;
    private int eventBusClusterPort = 0;
    private int eventBusClusterPublicPort = -1;
    private String eventBusClusterHost = null;
    private String eventBusClusterPublicHost = null;
    private String igniteWorkDirectory = "/tmp/ignite";
    private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    private long maxOffHeapMemory = DataStorageConfiguration.DFLT_DATA_REGION_MAX_SIZE;
    private int maxEventPayloadSize = 1024 * 1024 * 100; // 100MB
    private int maxNumberOfCoresToUse = Math.max(Runtime.getRuntime().availableProcessors(), 1);


    public DefaultContinuumProperties setMaxNumberOfCoresToUse(int maxNumberOfCoresToUse) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        this.maxNumberOfCoresToUse = maxNumberOfCoresToUse > 0 ? Math.min(availableProcessors, maxNumberOfCoresToUse) : Math.max(availableProcessors, 1);
        return this;
    }

    @Override
    public String toString() {
        ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                                    .append("debug", debug)
                                    .append("disableClustering", disableClustering)
                                    .append("sessionTimeout", sessionTimeout)
                                    .append("maxOffHeapMemory", maxOffHeapMemory);

        return sb.toString();
    }
}
