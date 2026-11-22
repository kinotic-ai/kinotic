package org.kinotic.testserver;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Stands in for entity persistence in latency and throughput measurements: answers a read with a fixed entity
 * after the delay the caller asks for, so a measurement can account for the time a real store spends.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.MANAGEMENT_API_ZONE)
public interface IBenchEntityService {

    /**
     * Reads the entity with the given id.
     *
     * @param id      the id the returned entity carries
     * @param delayMs how long to take before answering, in milliseconds; 0 answers at once
     * @return a future completing with a fixed entity of about one kilobyte carrying {@code id}
     */
    CompletableFuture<Map<String, Object>> findById(String id, int delayMs);

}
