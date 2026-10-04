package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * Binds the platform's staff when the platform has no administrator, once the server is up and the platform
 * store runs a model: every enabled platform operator as the platform's administrator, and every enabled
 * platform machine as a registrar of nodes. A platform a migration seeds has operators and a vm-manager but
 * no grant, and this gives it the ones it needs to be run; a platform with an administrator, whatever else
 * was granted or revoked since, is left as it is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrap {

    private static final long RETRY_MILLIS = 2_000;
    // The platform's operators and machines fit one page: a platform has a handful of each
    private static final int PAGE_SIZE = 1000;
    private static final String PLATFORM_OBJECT = AuthzUtil.object(AuthzUtil.PLATFORM_TYPE, AuthzUtil.PLATFORM_OBJECT_ID);

    private final Vertx vertx;
    private final ParticipantIdentityService identities;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        vertx.runOnContext(v -> run());
    }

    private void run() {
        stores.platformModelId()
              .compose(modelId -> administered())
              .compose(administered -> administered ? Future.succeededFuture(0) : bindStaff())
              .onSuccess(bound -> {
                  if (bound > 0) {
                      log.info("Bound {} platform operator(s) and machine(s) as the platform's staff", bound);
                  }
              })
              // the reconciler writes the platform model shortly after the servers start, so a store that runs
              // none yet, like one that cannot be reached, is asked again
              .onFailure(e -> {
                  log.debug("The platform staff sweep waits: {}", e.getMessage());
                  vertx.setTimer(RETRY_MILLIS, t -> run());
              });
    }

    private Future<Boolean> administered() {
        return relationships.findGrants(PLATFORM, PLATFORM_OBJECT)
                            .map(grants -> grants.stream().anyMatch(grant -> AuthzUtil.PLATFORM_ADMIN_ROLE.equals(grant.roleId())));
    }

    private Future<Integer> bindStaff() {
        Pageable page = Pageable.create(0, PAGE_SIZE, null);
        return identities.findUsersByScope(null, null, page)
                         .compose(users -> bindEach(users.getContent(), AuthzUtil.PLATFORM_ADMIN_ROLE))
                         .compose(bound -> identities.findMachinesByScope(null, null, page)
                                                     .compose(machines -> bindEach(machines.getContent(), VmNodeOrchestrationService.REGISTRAR_ROLE))
                                                     .map(more -> bound + more));
    }

    // Each enabled identity is bound under an id of its own, so every server sweeping at once binds it once
    private Future<Integer> bindEach(List<? extends ParticipantIdentity> staff, String roleId) {
        Future<Integer> ret = Future.succeededFuture(0);
        for (ParticipantIdentity identity : staff) {
            if (identity.isEnabled()) {
                String bindingId = roleId + "-of-" + identity.getId();
                ret = ret.compose(count -> relationships.ensureBound(PLATFORM, bindingId, roleId,
                                                                     AuthzUtil.object(AuthzUtil.USER_TYPE, identity.getId()),
                                                                     PLATFORM_OBJECT)
                                                        .map(count + 1));
            }
        }
        return ret;
    }
}
