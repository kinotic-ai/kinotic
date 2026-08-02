package org.kinotic.os.api.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.directory.ResolvableTypeConverter;
import org.kinotic.os.internal.api.services.DefaultApplicationService;
import org.kinotic.os.internal.api.services.DefaultProjectService;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.kinotic.idl.internal.directory.DefaultResolvableTypeConverter;
import org.kinotic.idl.internal.directory.DefaultSchemaFactory;
import org.kinotic.idl.internal.directory.ReactiveTypeConverter;
import org.kinotic.idl.internal.directory.TokenBufferTypeConverter;
import org.kinotic.idl.internal.directory.jdk.ArrayTypeConverter;
import org.kinotic.idl.internal.directory.jdk.BooleanTypeConverter;
import org.kinotic.idl.internal.directory.jdk.ByteTypeConverter;
import org.kinotic.idl.internal.directory.jdk.CharacterTypeConverter;
import org.kinotic.idl.internal.directory.jdk.DateTypeConverter;
import org.kinotic.idl.internal.directory.jdk.DoubleTypeConverter;
import org.kinotic.idl.internal.directory.jdk.EnumTypeConverter;
import org.kinotic.idl.internal.directory.jdk.FloatTypeConverter;
import org.kinotic.idl.internal.directory.jdk.IntegerTypeConverter;
import org.kinotic.idl.internal.directory.jdk.IterableTypeConverter;
import org.kinotic.idl.internal.directory.jdk.LongTypeConverter;
import org.kinotic.idl.internal.directory.jdk.MapTypeConverter;
import org.kinotic.idl.internal.directory.jdk.OptionalTypeConverter;
import org.kinotic.idl.internal.directory.jdk.ShortTypeConverter;
import org.kinotic.idl.internal.directory.jdk.StringTypeConverter;
import org.kinotic.idl.internal.directory.jdk.URITypeConverter;
import org.kinotic.idl.internal.directory.jdk.VoidTypeConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ReactiveAdapterRegistry;

import java.util.List;

/**
 * Verifies every MCP-exposed os-api service converts to a ServiceDefinition with the same converter set
 * the server wires at startup — an unconvertible type anywhere in a signature silently drops the whole
 * service from the directory, and with it every tool it provides.
 */
public class McpServiceSchemaTest {

    @Test
    public void mcpExposedServicesConvert() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory().createForServices(List.of(new ServiceDeclaration(ProjectService.class, DefaultProjectService.class),
                                                           new ServiceDeclaration(ApplicationService.class, DefaultApplicationService.class)));

        // createForServices omits any service that fails conversion, so a shrunken count is the failure signal
        Assertions.assertEquals(2, namespaceDefinition.getServices().size());
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

        // ProjectService's bare @McpTool states no hints, so deleteById serves the ones @McpToolInfo
        // states on CrudService in kinotic-core — a function ProjectService only inherits
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
        List<ResolvableTypeConverter> converters = List.of(new ArrayTypeConverter(),
                                                           new BooleanTypeConverter(),
                                                           new ByteTypeConverter(),
                                                           new CharacterTypeConverter(),
                                                           new DateTypeConverter(),
                                                           new DoubleTypeConverter(),
                                                           new EnumTypeConverter(),
                                                           new FloatTypeConverter(),
                                                           new IntegerTypeConverter(),
                                                           new IterableTypeConverter(),
                                                           new LongTypeConverter(),
                                                           new MapTypeConverter(),
                                                           new OptionalTypeConverter(),
                                                           new ShortTypeConverter(),
                                                           new StringTypeConverter(),
                                                           new URITypeConverter(),
                                                           new VoidTypeConverter(),
                                                           new TokenBufferTypeConverter(),
                                                           new ReactiveTypeConverter(sharedRegistryProvider()));
        return new DefaultSchemaFactory(new DefaultResolvableTypeConverter(converters));
    }

    // resolves to the shared ReactiveAdapterRegistry, as ReactiveTypeConverter falls back to outside a Spring context
    private static ObjectProvider<ReactiveAdapterRegistry> sharedRegistryProvider() {
        return new ObjectProvider<>() {
            @Override
            public ReactiveAdapterRegistry getIfAvailable() {
                return null;
            }
        };
    }

}
