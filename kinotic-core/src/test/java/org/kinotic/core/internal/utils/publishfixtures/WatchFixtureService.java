package org.kinotic.core.internal.utils.publishfixtures;

import org.kinotic.core.api.annotations.Publish;

/**
 * The published seam of the fixture hierarchy: only this interface carries {@link Publish}.
 */
@Publish
public interface WatchFixtureService {

    String watch(String id);

}
