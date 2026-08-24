package org.kinotic.core.internal.utils.publishfixtures;

/**
 * Unpublished engine contract extending the published seam, mirroring
 * {@code JobService extends JobWatchService}.
 */
public interface EngineFixtureService extends WatchFixtureService {

    String run(String definition);

}
