package org.kinotic.core.internal.utils.publishfixtures;

/**
 * Implementation that only names the engine contract, so the published seam is reachable
 * solely through the interface extends chain.
 */
public class DefaultEngineFixtureService implements EngineFixtureService {

    @Override
    public String watch(String id) {
        return id;
    }

    @Override
    public String run(String definition) {
        return definition;
    }

}
