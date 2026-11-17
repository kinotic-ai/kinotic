package org.kinotic.system.internal.api.services.billing;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.CommonStatsFlag;
import co.elastic.clients.elasticsearch._types.Level;
import co.elastic.clients.elasticsearch.indices.stats.IndicesStats;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.billing.UsageLine;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.system.internal.api.model.UsageReading;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the {@link UsageMeter#DATA_STORAGE_GB_MONTHS} of each application: the size of the primary copy of the data of
 * every entity it defines.
 */
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataStorageUsageSource implements UsageSource {

    private static final double BYTES_PER_GB = 1024.0 * 1024 * 1024;
    // the hours of an average month, so a level held for a whole month adds up to one GB-month
    private static final double HOURS_PER_MONTH = 730;
    private static final String DATA_STREAM_BACKING_PREFIX = ".ds-";

    private final ElasticsearchAsyncClient esAsyncClient;

    @Override
    public Future<List<UsageReading>> read() {
        return KinoticUtil.toFuture(esAsyncClient.indices().stats(b -> b.index(DomainUtil.INDEX_PREFIX + "*")
                                                                         .metric(CommonStatsFlag.Store)
                                                                         .level(Level.Indices)))
                          .map(response -> readings(response.indices()));
    }

    private static List<UsageReading> readings(Map<String, IndicesStats> indices) {
        // bytes by organization, then application
        Map<String, Map<String, Long>> bytes = new HashMap<>();
        indices.forEach((index, stats) -> {
            String[] owner = ownerOf(index);
            if (owner != null && stats.primaries() != null && stats.primaries().store() != null) {
                bytes.computeIfAbsent(owner[0], organizationId -> new HashMap<>())
                     .merge(owner[1], stats.primaries().store().sizeInBytes(), Long::sum);
            }
        });
        List<UsageReading> ret = new ArrayList<>();
        bytes.forEach((organizationId, byApplication) -> byApplication.forEach((applicationId, size) -> ret.add(
                new UsageReading(organizationId, new UsageLine().setMeter(UsageMeter.DATA_STORAGE_GB_MONTHS)
                                                                .setSubjectId(applicationId)
                                                                .setSubjectName(applicationId)
                                                                .setQuantity(size / BYTES_PER_GB / HOURS_PER_MONTH)))));
        return ret;
    }

    // An entity's index is kinotic_<organizationId>.<applicationId>.<entity>, and a data stream's backing indices put
    // .ds- in front and the date and generation after it; the system's own tables have no dots in their names
    private static String[] ownerOf(String index) {
        String name = index.startsWith(DATA_STREAM_BACKING_PREFIX) ? index.substring(DATA_STREAM_BACKING_PREFIX.length()) : index;
        String[] ret = null;
        if (name.startsWith(DomainUtil.INDEX_PREFIX)) {
            String[] parts = name.substring(DomainUtil.INDEX_PREFIX.length()).split("\\.", 3);
            if (parts.length == 3) {
                ret = new String[]{parts[0], parts[1]};
            }
        }
        return ret;
    }
}
