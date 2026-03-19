

package org.kinotic.util;

/**
 *
 * Created by navid on 9/16/19
 */
public interface Worker {

    void start();

    void shutdown(boolean interrupt) throws InterruptedException;

    String getName();
}
