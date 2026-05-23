package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.iam.DeviceApprovalService;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultDeviceApprovalService implements DeviceApprovalService {

    private final DeviceCodeGrantService deviceCodeGrantService;

    @Override
    public CompletableFuture<Void> approve(String userCode, Participant participant) {
        return deviceCodeGrantService.approve(userCode, participant.getId());
    }
}
