package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import org.kinotic.management.api.model.billing.UsageMeasurementType;
import org.kinotic.system.internal.api.model.UsageReading;

import java.util.List;

/**
 * Reads the level of a {@link UsageMeasurementType#POLLED_LEVEL} meter for every organization, once each hour. Each
 * reading stands for the whole hour it is read in, so a source returns the quantity one hour at the current level
 * comes to. A Spring bean implementing it is read by {@link UsageMetering} with no other wiring.
 */
public interface UsageSource {

    /**
     * Reads the current level of every organization that uses any.
     *
     * @return a reading for each organization and thing using it
     */
    Future<List<UsageReading>> read();
}
