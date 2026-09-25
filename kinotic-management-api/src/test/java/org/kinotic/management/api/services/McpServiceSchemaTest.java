package org.kinotic.management.api.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.directory.ResolvableTypeConverter;
import org.kinotic.management.api.services.deployment.MicroserviceDeploymentService;
import org.kinotic.management.api.services.deployment.UiDeploymentService;
import org.kinotic.management.internal.api.services.DefaultApplicationService;
import org.kinotic.management.internal.api.services.DefaultProjectService;
import org.kinotic.management.internal.api.services.deployment.DefaultMicroserviceDeploymentService;
import org.kinotic.management.internal.api.services.deployment.DefaultUiDeploymentService;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.kinotic.idl.internal.directory.DefaultResolvableTypeConverter;
import org.kinotic.idl.internal.directory.DefaultSchemaFactory;
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
import io.vertx.core.Future;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

/**
 * Verifies every MCP-exposed management-api service converts to a ServiceDefinition with the same converter set
 * the server wires at startup — an unconvertible type anywhere in a signature silently drops the whole
 * service from the directory, and with it every tool it provides.
 */
public class McpServiceSchemaTest {

    @Test
    public void mcpExposedServicesConvert() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(ProjectService.class, DefaultProjectService.class),
                                                           new ServiceDeclaration(ApplicationService.class, DefaultApplicationService.class),
                                                           new ServiceDeclaration(MicroserviceDeploymentService.class, DefaultMicroserviceDeploymentService.class),
                                                           new ServiceDeclaration(UiDeploymentService.class, DefaultUiDeploymentService.class)));

        // createForServices omits any service that fails conversion, so a shrunken count is the failure signal
        Assertions.assertEquals(4, namespaceDefinition.getServices().size());
    }

    @Test
    public void inheritedJavadocDescriptionsResolveAcrossModules() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(TestCrudSweptService.class, TestCrudSweptService.class)));

        ServiceDefinition service = namespaceDefinition.getServices()
                                                       .stream()
                                                       .findFirst()
                                                       .orElseThrow();
        FunctionDefinition findById = service.getFunctions()
                                             .stream()
                                             .filter(function -> function.getName().equals("findById"))
                                             .findFirst()
                                             .orElseThrow();

        // the description is CrudService.findById's Javadoc, read from the kinotic-core jar resource the
        // McpToolDocProcessor emitted when kinotic-core compiled — guarding the conventions wiring and the
        // cross-module ancestor walk together
        McpToolC3Decorator decorator = findById.findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(decorator);
        Assertions.assertEquals("Retrieves an entity by its id.", decorator.getDescription());
    }

    @Test
    public void inheritedCrudHintsResolveAcrossModules() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(ProjectService.class, DefaultProjectService.class)));

        ServiceDefinition service = namespaceDefinition.getServices()
                                                       .stream()
                                                       .findFirst()
                                                       .orElseThrow();

        // ProjectService's bare @McpTool states no hints, so syncIndex serves the one @McpToolInfo states
        // on CrudService in kinotic-core — a function ProjectService only inherits, and one whose name
        // matches no rule, so nothing but the annotation can be the source
        McpToolC3Decorator syncIndex = mcpTool(service, "syncIndex");
        Assertions.assertTrue(syncIndex.isIdempotentHint());
        Assertions.assertFalse(syncIndex.isReadOnlyHint());
        Assertions.assertFalse(syncIndex.isDestructiveHint());

        McpToolC3Decorator deleteById = mcpTool(service, "deleteById");
        Assertions.assertTrue(deleteById.isDestructiveHint());
        Assertions.assertTrue(deleteById.isIdempotentHint());
        Assertions.assertFalse(deleteById.isReadOnlyHint());

        // nothing states hints for a function ProjectService declares itself, so its name decides
        McpToolC3Decorator createIfNotExist = mcpTool(service, "createProjectIfNotExist");
        Assertions.assertTrue(createIfNotExist.isIdempotentHint());
        Assertions.assertFalse(createIfNotExist.isDestructiveHint());
        Assertions.assertEquals("Project Service Create Project If Not Exist", createIfNotExist.getTitle());
    }

    @Test
    public void openWorldHintSeparatesFunctionsThatCallOut() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(ProjectService.class, DefaultProjectService.class)));

        ServiceDefinition service = namespaceDefinition.getServices()
                                                       .stream()
                                                       .findFirst()
                                                       .orElseThrow();

        // MCP defaults openWorldHint to true, so every function that only touches the platform's own data
        // has to say false for a host to read it as the closed-domain call it is
        Assertions.assertFalse(mcpTool(service, "findByRepoFullName").isOpenWorldHint());
        Assertions.assertFalse(mcpTool(service, "createProjectIfNotExist").isOpenWorldHint());
        Assertions.assertFalse(mcpTool(service, "deleteById").isOpenWorldHint());

        // retryRepoInitialization delegates to the repo provisioner, which reaches GitHub
        McpToolC3Decorator retry = mcpTool(service, "retryRepoInitialization");
        Assertions.assertTrue(retry.isOpenWorldHint());
        // the @McpTool that states the open world states the rest of the hints too, matching what the
        // function name implied on its own
        Assertions.assertFalse(retry.isReadOnlyHint());
        Assertions.assertFalse(retry.isDestructiveHint());
        Assertions.assertFalse(retry.isIdempotentHint());
    }

    private static McpToolC3Decorator mcpTool(ServiceDefinition service, String functionName) {
        McpToolC3Decorator ret = service.getFunctions()
                                        .stream()
                                        .filter(function -> function.getName().equals(functionName))
                                        .findFirst()
                                        .orElseThrow()
                                        .findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(ret, functionName + " is not exposed as a tool");
        return ret;
    }

    private static DefaultSchemaFactory schemaFactory() {
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
                                                           new ReactiveToC3Type(registryProvider()));
        return new DefaultSchemaFactory(new DefaultResolvableTypeConverter(converters));
    }

    // a registry carrying the Vert.x Future adapter DefaultKinotic registers at startup, so the
    // converter set here matches what the server wires — the shared registry alone cannot convert
    // the Future returns the CRUD interfaces declare
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
