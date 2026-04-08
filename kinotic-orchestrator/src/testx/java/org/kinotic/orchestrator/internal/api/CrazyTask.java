

package org.kinotic.orchestrator.internal.api;


import java.util.concurrent.Callable;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class CrazyTask implements Callable<String> {

    private final CrazyGrind crazyGrind;

    public CrazyTask(CrazyGrind crazyGrind) {
        this.crazyGrind = crazyGrind;
    }

    @Override
    public String call() throws Exception {
        return crazyGrind.getSlogan();
    }

}
