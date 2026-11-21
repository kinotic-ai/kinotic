package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.management.api.model.Capabilities;
import org.kinotic.management.api.services.FeedbackService;
import org.kinotic.management.api.services.CapabilityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class DefaultCapabilityService implements CapabilityService {

    private final Capabilities capabilities;

    // each feature is on exactly when the bean that serves it exists
    public DefaultCapabilityService(ObjectProvider<FeedbackService> feedbackService) {
        this.capabilities = new Capabilities().setFeedbackEnabled(feedbackService.getIfAvailable() != null);
    }

    @Override
    public Future<Capabilities> findCapabilities() {
        return Future.succeededFuture(capabilities);
    }

}
