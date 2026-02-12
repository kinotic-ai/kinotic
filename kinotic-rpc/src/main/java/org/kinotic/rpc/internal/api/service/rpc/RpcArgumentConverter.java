

package org.kinotic.rpc.internal.api.service.rpc;

import java.lang.reflect.Method;

/**
 * Converts arguments that are passed to a service proxy.
 *
 *
 * Created by navid on 2019-04-19.
 */
public interface RpcArgumentConverter {

    /**
     * @return a string with the mime type for the content type produced by this converters convert method ex: application/json
     */
    String producesContentType();

    /**
     * Converts the arguments into a Buffer that can be sent across the event bus.
     * @param method that is being invoked
     * @param args the arguments passed to the invoked method
     * @return a byte[] with the data to be sent
     */
    byte[] convert(Method method, Object[] args);

}
