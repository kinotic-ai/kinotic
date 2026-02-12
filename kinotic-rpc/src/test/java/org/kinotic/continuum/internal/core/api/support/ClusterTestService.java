

package org.kinotic.continuum.internal.core.api.support;

import org.kinotic.rpc.api.annotations.Publish;

/**
 *
 * Created by navid on 10/17/19
 */
@Publish
public interface ClusterTestService {

    Long getFreeMemory();

    String getData();
}
