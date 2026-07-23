package org.kinotic.gateway.internal.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Reports unreachable services to the {@link ServiceDirectory} without ever blocking or failing the caller's
 * request path. With no directory present, reports are dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceUnreachableReporter {

    private final ObjectProvider<ServiceDirectory> serviceDirectoryProvider;

    /**
     * Fire-and-forget report that the given CRI could not be reached.
     * @param cri the CRI that could not be reached
     */
    public void report(String cri) {
        ServiceDirectory serviceDirectory = serviceDirectoryProvider.getIfAvailable();
        if (serviceDirectory != null) {
            // reportUnreachable debounces per CRI and only ever writes a verified state
            serviceDirectory.reportUnreachable(cri)
                            .exceptionally(throwable -> {
                                log.debug("Failed to report unreachable service {}", cri, throwable);
                                return null;
                            });
        }
    }
}
