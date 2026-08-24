package org.kinotic.core.internal.utils.publishfixtures;

/**
 * Reaches the published seam twice: directly and through the engine contract.
 */
public class DiamondEngineFixtureService implements EngineFixtureService, WatchFixtureService {

    @Override
    public String watch(String id) {
        return id;
    }

    @Override
    public String run(String definition) {
        return definition;
    }

}
