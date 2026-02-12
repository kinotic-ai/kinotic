

package org.kinotic.rpc.api;

import org.kinotic.rpc.api.service.ServiceDescriptor;
import org.kinotic.rpc.api.service.ServiceIdentifier;

/**
 * ServiceDirectory is responsible for keeping track of all registered service contracts
 *
 *
 * Created by navid on 2019-06-11.
 */
public interface ServiceDirectory {

    void register(ServiceDescriptor serviceDescriptor);

    void unregister(ServiceIdentifier serviceIdentifier);

}
