

package org.kinotic.rpc.api.crud;

/**
 * Instances of this interface have a unique id (identity) and are therefore "Identifiable"
 *
 *
 * Created by navid on 2/3/20
 */
public interface Identifiable<T> {

    T getId();

}
