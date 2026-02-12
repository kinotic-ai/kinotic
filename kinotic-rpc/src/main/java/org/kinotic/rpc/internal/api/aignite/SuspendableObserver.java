

package org.kinotic.rpc.internal.api.aignite;

public interface SuspendableObserver<T> extends Observer<T> {

    /**
     * Temporarily stop sending data to the handler
     * @return this
     */
    SuspendableObserver<T> suspend();

    /**
     * Resume sending data to the handler
     * @return this
     */
    SuspendableObserver<T> resume();

}
