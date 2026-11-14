package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.security.AuthzGraphSeedJob;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.services.workload.VmNodeService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Places the system's staff and nodes in the authorization graph when the system has no administrator: every
 * node on the system, every enabled system machine as a registrar of nodes, and every enabled system user as the
 * system's administrator. A system whose system users, vm-manager and nodes a migration or a script wrote holds
 * no tuples for them, and this gives it the ones it needs to be run; a system with an administrator, whatever
 * else was granted or revoked since, is left as it is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemGraphSeedJob implements AuthzGraphSeedJob {

    // The system users, machines and nodes fit one page: a system has a handful of each
    private static final int PAGE_SIZE = 1000;

    private final AuthzModelService models;
    private final ParticipantIdentityService identities;
    private final VmNodeRepository nodes;
    private final RelationshipService relationships;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Places the system's staff and nodes in the authorization graph when the system has no administrator")
                            .name("seed-system-graph")
                            .version("1")
                            .task(Tasks.fromCallable("Wait for the system model",
                                                     () -> models.awaitSystemModel(List.of(VmNodeOrchestrationService.REGISTRAR_ROLE,
                                                                                           AuthzConstants.SYSTEM_ADMIN_ROLE))))
                            .task(Tasks.fromCallable("Place the system's staff and nodes", this::placeStaff));
    }

    private Future<Void> placeStaff() {
        return relationships.findGrants(SYSTEM, AuthzConstants.SYSTEM_OBJECT).compose(grants -> {
            Future<Void> ret;
            if (grants.stream().anyMatch(grant -> AuthzConstants.SYSTEM_ADMIN_ROLE.equals(grant.roleId()))) {
                ret = Future.succeededFuture();
            } else {
                Pageable page = Pageable.create(0, PAGE_SIZE, null);
                // The administrators are bound last, so a run that failed before them finds no administrator on
                // the next start and places everything again; every binding has an id of its own, so one made
                // twice is held once. They are read first: a script writes the system's machines before its first
                // user, so a run that reads the user reads every machine after it
                ret = identities.findSystemUsers(page).compose(users -> nodes.findAll(page)
                           .compose(found -> relationships.ensure(SYSTEM, found.getContent().stream()
                                                                               .map(node -> AuthzGraphUtil.containment(AuthzConstants.SYSTEM_OBJECT,
                                                                                                                       AuthzUtil.object(VmNodeService.RESOURCE_TYPE, node.getId())))
                                                                               .toList()))
                           .compose(v -> identities.findMachinesByScope(null, null, page))
                           .compose(machines -> bindEach(machines.getContent().stream()
                                                                 .filter(MachineParticipantIdentity::isEnabled)
                                                                 .map(machine -> AuthzUtil.object(AuthzConstants.USER_TYPE, machine.getId()))
                                                                 .toList(),
                                                         VmNodeOrchestrationService.REGISTRAR_ROLE))
                           .compose(v -> bindEach(users.getContent().stream()
                                                       .filter(SystemParticipantIdentity::isEnabled)
                                                       .map(user -> AuthzUtil.object(AuthzConstants.USER_TYPE, user.getId()))
                                                       .toList(),
                                                  AuthzConstants.SYSTEM_ADMIN_ROLE)));
            }
            return ret;
        });
    }

    private Future<Void> bindEach(List<String> users, String roleId) {
        Future<Void> ret = Future.succeededFuture();
        for (String user : users) {
            String bindingId = AuthzUtil.roleBindingIdOf(roleId, AuthzUtil.idOf(user));
            ret = ret.compose(v -> relationships.ensureBound(SYSTEM, bindingId, roleId, user, AuthzConstants.SYSTEM_OBJECT));
        }
        return ret.onSuccess(v -> log.info("Bound {} system user(s) or machine(s) as {}", users.size(), roleId));
    }
}
