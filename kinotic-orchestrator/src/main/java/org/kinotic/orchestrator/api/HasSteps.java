

package org.kinotic.orchestrator.api;

import java.util.List;

/**
 *
 * Created by Navid Mitchell on 11/12/20
 */
public interface HasSteps {

    /**
     * @return the {@link Step}'s defined for this
     */
    List<Step> getSteps();

}
