package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.Participant;
import org.kinotic.idl.api.directory.ResolvableTypeConverter;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.internal.directory.C3SchemaToC3Type;
import org.kinotic.idl.internal.directory.DefaultResolvableTypeConverter;
import org.kinotic.idl.internal.directory.DefaultSchemaService;
import org.kinotic.idl.internal.directory.JsonNodeToC3Type;
import org.kinotic.idl.internal.directory.ReactiveToC3Type;
import org.kinotic.idl.internal.directory.TokenBufferToC3Type;
import org.kinotic.idl.internal.directory.jdk.ArrayToC3Type;
import org.kinotic.idl.internal.directory.jdk.BooleanToC3Type;
import org.kinotic.idl.internal.directory.jdk.ByteToC3Type;
import org.kinotic.idl.internal.directory.jdk.CharacterToC3Type;
import org.kinotic.idl.internal.directory.jdk.DateToC3Type;
import org.kinotic.idl.internal.directory.jdk.DoubleToC3Type;
import org.kinotic.idl.internal.directory.jdk.EnumToC3Type;
import org.kinotic.idl.internal.directory.jdk.FloatToC3Type;
import org.kinotic.idl.internal.directory.jdk.IntegerToC3Type;
import org.kinotic.idl.internal.directory.jdk.IterableToC3Type;
import org.kinotic.idl.internal.directory.jdk.LongToC3Type;
import org.kinotic.idl.internal.directory.jdk.MapToC3Type;
import org.kinotic.idl.internal.directory.jdk.OptionalToC3Type;
import org.kinotic.idl.internal.directory.jdk.ShortToC3Type;
import org.kinotic.idl.internal.directory.jdk.StringToC3Type;
import org.kinotic.idl.internal.directory.jdk.URIToC3Type;
import org.kinotic.idl.internal.directory.jdk.VoidToC3Type;
import org.kinotic.system.api.services.deployment.DeploymentOperationsService;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.services.workload.VmNodeService;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.kinotic.system.api.services.workload.WorkloadService;
import org.kinotic.system.internal.api.services.DefaultKinoticClusterInfoService;
import org.kinotic.system.internal.api.services.DefaultSystemAccessService;
import org.kinotic.system.internal.api.services.DefaultSystemMemberService;
import org.kinotic.system.internal.api.services.DefaultSystemOrganizationService;
import org.kinotic.system.internal.api.services.DefaultWatchEventService;
import org.kinotic.system.internal.api.services.deployment.DefaultDeploymentOperationsService;
import org.kinotic.system.internal.api.services.workload.DefaultVmNodeOrchestrationService;
import org.kinotic.system.internal.api.services.workload.DefaultVmNodeService;
import org.kinotic.system.internal.api.services.workload.DefaultWorkloadOrchestrationService;
import org.kinotic.system.internal.api.services.workload.DefaultWorkloadService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Verifies every system-api service the directory publishes as an authorization resource converts to a
 * ServiceDefinition with the same converter set the server wires at startup: a function whose check cannot
 * be derived rejects the service's registration, and with it the server's startup. Pins the checks the
 * system tier is built on: a node's registration on the platform, its reports on the node, and the
 * platform's own functions on its fixed object.
 */
public class SystemServiceSchemaTest {

    @Test
    public void everyResourceServiceConverts() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(KinoticClusterInfoService.class, DefaultKinoticClusterInfoService.class),
                                                           new ServiceDeclaration(WatchEventService.class, DefaultWatchEventService.class),
                                                           new ServiceDeclaration(SystemMemberService.class, DefaultSystemMemberService.class),
                                                           new ServiceDeclaration(SystemOrganizationService.class, DefaultSystemOrganizationService.class),
                                                           new ServiceDeclaration(SystemAccessService.class, DefaultSystemAccessService.class),
                                                           new ServiceDeclaration(VmNodeService.class, DefaultVmNodeService.class),
                                                           new ServiceDeclaration(VmNodeOrchestrationService.class, DefaultVmNodeOrchestrationService.class),
                                                           new ServiceDeclaration(WorkloadService.class, DefaultWorkloadService.class),
                                                           new ServiceDeclaration(WorkloadOrchestrationService.class, DefaultWorkloadOrchestrationService.class),
                                                           new ServiceDeclaration(DeploymentOperationsService.class, DefaultDeploymentOperationsService.class)));

        Assertions.assertEquals(10, namespaceDefinition.getServices().size());
        for (ServiceDefinition service : namespaceDefinition.getServices()) {
            Assertions.assertNotNull(service.findDecorator(AuthzResourceC3Decorator.class), service.getName() + " declares no resource");
            for (FunctionDefinition function : service.getFunctions()) {
                Assertions.assertNotNull(function.findDecorator(AuthzCheckC3Decorator.class),
                                         service.getName() + "." + function.getName() + " carries no check");
            }
        }
    }

    @Test
    public void aNodeRegistersOnThePlatformAndReportsOnItself() {
        ServiceDefinition service = schemaFactory().createForServices(List.of(new ServiceDeclaration(VmNodeOrchestrationService.class,
                                                                                                       DefaultVmNodeOrchestrationService.class)))
                                                   .getServices()
                                                   .iterator()
                                                   .next();

        AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
        Assertions.assertEquals(List.of(VmNodeOrchestrationService.REGISTRAR_ROLE, VmNodeOrchestrationService.AGENT_ROLE),
                                resource.getRoles().stream().map(role -> role.getId()).toList());

        AuthzCheckC3Decorator register = check(service, "registerNode");
        Assertions.assertEquals("platform:kinotic", register.getResource() + ":" + register.getObjectId());
        Assertions.assertEquals("vm_node", register.getPermissionResource());
        Assertions.assertEquals("can_register_node", register.getPermission());

        AuthzCheckC3Decorator heartbeat = check(service, "heartbeat");
        Assertions.assertEquals("vm_node:{nodeId}", heartbeat.getResource() + ":" + heartbeat.getObjectId());
        Assertions.assertEquals("can_report", heartbeat.getPermission());
        Assertions.assertEquals("can_report", check(service, "reportWorkloadStatus").getPermission());
        Assertions.assertEquals("can_report", check(service, "deregisterNode").getPermission());
        Assertions.assertEquals("can_edit", check(service, "verifyNode").getPermission());
        // a listing of nodes with room names none, so it is checked on the platform, which every node is on
        Assertions.assertEquals("platform:kinotic", check(service, "findAvailableNode").getResource() + ":" + check(service, "findAvailableNode").getObjectId());
        Assertions.assertEquals("can_view", check(service, "findAvailableNode").getPermission());
    }

    @Test
    public void thePlatformsOwnFunctionsAreCheckedOnItsFixedObject() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(SystemMemberService.class, DefaultSystemMemberService.class),
                                                           new ServiceDeclaration(WorkloadService.class, DefaultWorkloadService.class),
                                                           new ServiceDeclaration(WorkloadOrchestrationService.class, DefaultWorkloadOrchestrationService.class),
                                                           new ServiceDeclaration(SystemOrganizationService.class, DefaultSystemOrganizationService.class)));

        ServiceDefinition members = service(namespaceDefinition, "SystemMemberService");
        Assertions.assertEquals("platform:kinotic", check(members, "createMachine").getResource() + ":" + check(members, "createMachine").getObjectId());
        Assertions.assertEquals("can_manage_machines", check(members, "createMachine").getPermission());
        Assertions.assertTrue(check(members, "rotateSecret").isConsistent());

        // the record functions a workload service inherits are checked on the platform: the reads with the permission
        // their redeclaration names, the writes with the service's
        ServiceDefinition workloads = service(namespaceDefinition, "WorkloadService");
        Assertions.assertEquals("platform_can_view_workloads", check(workloads, "findById").getPermissionResource() + "_" + check(workloads, "findById").getPermission());
        Assertions.assertEquals("can_manage_workloads", check(workloads, "deleteById").getPermission());
        Assertions.assertEquals("can_manage_workloads", check(workloads, "save").getPermission());
        Assertions.assertEquals("can_manage_workloads", check(workloads, "syncIndex").getPermission());
        // every function of the orchestration manages workloads, whatever its verb
        ServiceDefinition orchestration = service(namespaceDefinition, "WorkloadOrchestrationService");
        Assertions.assertEquals("can_manage_workloads", check(orchestration, "deployWorkload").getPermission());
        Assertions.assertEquals("can_manage_workloads", check(orchestration, "deleteWorkloads").getPermission());

        // a read of one organization is checked on it, a read across them on the platform
        ServiceDefinition organizations = service(namespaceDefinition, "SystemOrganizationService");
        Assertions.assertEquals("organization:{organizationId}", check(organizations, "findApplications").getResource() + ":" + check(organizations, "findApplications").getObjectId());
        Assertions.assertEquals("can_view", check(organizations, "findApplications").getPermission());
        Assertions.assertEquals("can_view_members", check(organizations, "findMembers").getPermission());
        Assertions.assertEquals("platform:kinotic", check(organizations, "findOrganizations").getResource() + ":" + check(organizations, "findOrganizations").getObjectId());
        Assertions.assertEquals("organization", check(organizations, "findOrganizations").getPermissionResource());
    }

    private static ServiceDefinition service(NamespaceDefinition namespaceDefinition, String name) {
        return namespaceDefinition.getServices()
                                  .stream()
                                  .filter(service -> service.getName().equals(name))
                                  .findFirst()
                                  .orElseThrow();
    }

    private static AuthzCheckC3Decorator check(ServiceDefinition service, String functionName) {
        AuthzCheckC3Decorator ret = service.getFunctions()
                                           .stream()
                                           .filter(function -> function.getName().equals(functionName))
                                           .findFirst()
                                           .orElseThrow()
                                           .findDecorator(AuthzCheckC3Decorator.class);
        Assertions.assertNotNull(ret, functionName + " carries no check");
        return ret;
    }

    private static DefaultSchemaService schemaFactory() {
        List<ResolvableTypeConverter> converters = List.of(new ArrayToC3Type(),
                                                           new BooleanToC3Type(),
                                                           new ByteToC3Type(),
                                                           new CharacterToC3Type(),
                                                           new DateToC3Type(),
                                                           new DoubleToC3Type(),
                                                           new EnumToC3Type(),
                                                           new FloatToC3Type(),
                                                           new IntegerToC3Type(),
                                                           new IterableToC3Type(),
                                                           new LongToC3Type(),
                                                           new MapToC3Type(),
                                                           new OptionalToC3Type(),
                                                           new ShortToC3Type(),
                                                           new StringToC3Type(),
                                                           new URIToC3Type(),
                                                           new VoidToC3Type(),
                                                           new TokenBufferToC3Type(),
                                                           new JsonNodeToC3Type(),
                                                           new C3SchemaToC3Type(),
                                                           new ReactiveToC3Type(registryProvider()));
        return new DefaultSchemaService(new DefaultResolvableTypeConverter(converters), Set.of(Participant.class));
    }

    // a registry carrying the Vert.x Future adapter DefaultKinotic registers at startup, so the converter set
    // here matches what the server wires
    private static ObjectProvider<ReactiveAdapterRegistry> registryProvider() {
        ReactiveAdapterRegistry registry = new ReactiveAdapterRegistry();
        registry.registerReactiveType(ReactiveTypeDescriptor.singleOptionalValue(Future.class,
                                                                                 (Supplier<Future<?>>) Future::succeededFuture),
                                      source -> Mono.fromCompletionStage(((Future<?>) source).toCompletionStage()),
                                      publisher -> Future.fromCompletionStage(Mono.from(publisher).toFuture()));
        return new ObjectProvider<>() {
            @Override
            public ReactiveAdapterRegistry getIfAvailable() {
                return registry;
            }
        };
    }

}
