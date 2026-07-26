package org.kinotic.os.api.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.ResolvableTypeConverter;
import org.kinotic.idl.api.schema.NamespaceDefinition;
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
        DefaultSchemaFactory schemaFactory = new DefaultSchemaFactory(new DefaultResolvableTypeConverter(converters));

        NamespaceDefinition namespaceDefinition =
                schemaFactory.createForServices(List.of(ProjectService.class, ApplicationService.class));

        // createForServices omits any service that fails conversion, so a shrunken count is the failure signal
        Assertions.assertEquals(2, namespaceDefinition.getServices().size());
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
