package org.kinotic.core.internal.utils.publishfixtures;

/**
 * Unpublished engine contract extending the published seam, mirroring
 * {@code JobService} inheriting a published contract through a superinterface.
 */
public interface EngineFixtureService extends WatchFixtureService {

    String run(String definition);

}
