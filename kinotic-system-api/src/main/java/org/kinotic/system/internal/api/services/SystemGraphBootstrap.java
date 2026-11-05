package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.services.workload.VmNodeService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Places the system's staff and nodes in the authorization graph when the system has no administrator, once the
 * server is up and the system store runs a model: every node on the system, every enabled system machine as a
 * registrar of nodes, and every enabled system user as the system's administrator. A system a migration seeds has
 * system users, a vm-manager and nodes but no tuples, and this gives it the ones it needs to be run; a system with
 * an administrator, whatever else was granted or revoked since, is left as it is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemGraphBootstrap {

    private static final long RETRY_MILLIS = 2_000;
    // a staff member who could not be bound is tried again by a later sweep, after a delay that doubles with each sweep
    // that leaves one, since a model the system store runs at startup may lack the types the sweep writes
    private static final long MAX_RETRY_MILLIS = 60_000;
    // The system users, machines and nodes fit one page: a system has a handful of each
    private static final int PAGE_SIZE = 1000;

    private final Vertx vertx;
    private final ParticipantIdentityService identities;
    private final VmNodeRepository nodes;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        vertx.runOnContext(v -> run());
    }

    private void run() {
        stores.modelId(SYSTEM)
              .compose(modelId -> relationships.findGrants(SYSTEM, AuthzConstants.SYSTEM_OBJECT))
              .onSuccess(grants -> {
                  // the administrators are bound last, so a sweep cut short before them is made again
                  if (grants.stream().noneMatch(grant -> AuthzConstants.SYSTEM_ADMIN_ROLE.equals(grant.roleId()))) {
                      place(RETRY_MILLIS);
                  }
              })
              // the reconciler writes the system model shortly after the servers start, so a store that runs
              // none yet, like one that cannot be reached, is asked again
              .onFailure(e -> {
                  log.debug("The system staff sweep waits: {}", e.getMessage());
                  vertx.setTimer(RETRY_MILLIS, t -> run());
              });
    }

    // The first administrator ends the sweep for every later start, so a sweep that fails part way is made again here,
    // without asking the store, until it places everything; every binding has an id of its own, so one made twice is held once
    private void place(long failedDelay) {
        AtomicInteger failed = new AtomicInteger();
        placeStaff(failed).onSuccess(bound -> {
                              log.info("Bound {} system user(s) and machine(s) as the system's staff", bound);
                              if (failed.get() > 0) {
                                  log.warn("{} system user(s) and machine(s) could not be bound; the system staff sweep is made again", failed.get());
                                  vertx.setTimer(failedDelay, t -> place(Math.min(failedDelay * 2, MAX_RETRY_MILLIS)));
                              }
                          })
                          .onFailure(e -> {
                              log.warn("The system staff sweep is made again: {}", e.getMessage());
                              vertx.setTimer(RETRY_MILLIS, t -> place(failedDelay));
                          });
    }

    private Future<Integer> placeStaff(AtomicInteger failed) {
        Pageable page = Pageable.create(0, PAGE_SIZE, null);
        return nodes.findAll(page)
                    .compose(found -> relationships.ensure(SYSTEM, found.getContent().stream()
                                                                         .map(node -> AuthzGraphUtil.containment(AuthzConstants.SYSTEM_OBJECT,
                                                                                                                 AuthzUtil.object(VmNodeService.RESOURCE_TYPE, node.getId())))
                                                                         .toList()))
                    .compose(v -> identities.findMachinesByScope(null, null, page))
                    .compose(machines -> bindEach(machines.getContent().stream()
                                                          .filter(MachineParticipantIdentity::isEnabled)
                                                          .map(machine -> AuthzUtil.object(AuthzConstants.USER_TYPE, machine.getId()))
                                                          .toList(),
                                                  VmNodeOrchestrationService.REGISTRAR_ROLE,
                                                  failed))
                    .compose(bound -> identities.findSystemUsers(page)
                                                .compose(users -> bindEach(users.getContent().stream()
                                                                                .filter(SystemParticipantIdentity::isEnabled)
                                                                                .map(user -> AuthzUtil.object(AuthzConstants.USER_TYPE, user.getId()))
                                                                                .toList(),
                                                                           AuthzConstants.SYSTEM_ADMIN_ROLE,
                                                                           failed))
                                                .map(more -> bound + more));
    }

    // Each user is bound under an id of its own, so every server sweeping at once binds it once; one that fails
    // leaves the others to be bound
    private Future<Integer> bindEach(List<String> users, String roleId, AtomicInteger failed) {
        Future<Integer> ret = Future.succeededFuture(0);
        for (String user : users) {
            String bindingId = AuthzUtil.roleBindingIdOf(roleId, AuthzUtil.idOf(user));
            ret = ret.compose(count -> relationships.ensureBound(SYSTEM, bindingId, roleId, user, AuthzConstants.SYSTEM_OBJECT)
                                                    .map(count + 1)
                                                    .recover(e -> {
                                                        log.warn("{} could not be bound as {}: {}", user, roleId, e.getMessage());
                                                        failed.incrementAndGet();
                                                        return Future.succeededFuture(count);
                                                    }));
        }
        return ret;
    }
}
