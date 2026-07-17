package org.kinotic.core.internal.api.directory;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.annotations.McpTool;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.converter.jsonschema.McpJsonSchemaGenerator;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Captures MCP-exposed published services into the {@link ServiceDirectory} at registration time.
 * <p>
 * The directory is resolved optionally, so a standalone core deployment with no directory implementation does no
 * capture work at all. When a directory is present, a service is captured only if at least one of its methods carries
 * {@link McpTool}; the C3 contract, MCP tool decorators, and ready-to-serve tool definitions are built once here and
 * stored on the entry.
 */
@Slf4j
@Component
public class ServiceDirectoryCapture {

    private final SchemaFactory schemaFactory;
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    private final ObjectProvider<ServiceDirectory> serviceDirectoryProvider;
    private final McpJsonSchemaGenerator schemaGenerator;
    private final String sourceVersion;

    public ServiceDirectoryCapture(SchemaFactory schemaFactory,
                                     IdlConverterFactory idlConverterFactory,
                                     ReactiveAdapterRegistry reactiveAdapterRegistry,
                                     ObjectProvider<ServiceDirectory> serviceDirectoryProvider) {
        this.schemaFactory = schemaFactory;
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
        this.serviceDirectoryProvider = serviceDirectoryProvider;
        this.schemaGenerator = new McpJsonSchemaGenerator(idlConverterFactory);
        this.sourceVersion = ServiceDirectoryCapture.class.getPackage().getImplementationVersion();
    }

    /**
     * Captures the service into the directory if a directory implementation is present and the interface is
     * MCP-exposed.
     * @param serviceIdentifier the identifier the service registered under
     * @param serviceInterface the {@code @Publish} interface being registered
     * @throws IllegalStateException if an {@code @McpTool} function has a streaming return type or two functions
     *                               produce the same tool name
     */
    public void capture(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        ServiceDirectory directory = serviceDirectoryProvider.getIfAvailable();
        if (directory == null) {
            return;
        }
        Map<String, Method> mcpMethods = mcpMethods(serviceInterface);
        if (mcpMethods.isEmpty()) {
            return;
        }

        ServiceDirectoryEntry entry = buildEntry(serviceIdentifier, serviceInterface, mcpMethods);
        directory.register(entry).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.error("Failed to register service {} in the ServiceDirectory", serviceIdentifier, throwable);
            }
        });
    }

    /**
     * Marks a previously captured service offline. No-op when no directory is present or the interface is not
     * MCP-exposed.
     * @param serviceIdentifier the identifier the service registered under
     * @param serviceInterface the {@code @Publish} interface being unregistered
     */
    public void remove(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        ServiceDirectory directory = serviceDirectoryProvider.getIfAvailable();
        if (directory == null || mcpMethods(serviceInterface).isEmpty()) {
            return;
        }
        directory.unregister(entryId(serviceIdentifier)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.error("Failed to mark service {} offline in the ServiceDirectory", serviceIdentifier, throwable);
            }
        });
    }

    private ServiceDirectoryEntry buildEntry(ServiceIdentifier serviceIdentifier,
                                             Class<?> serviceInterface,
                                             Map<String, Method> mcpMethods) {
        NamespaceDefinition namespace = schemaFactory.createForService(serviceInterface);
        ServiceDefinition contract = namespace.getServices().iterator().next();
        Map<String, ObjectC3Type> referenceResolver = referenceResolver(namespace);

        List<McpToolDefinition> tools = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();

        for (FunctionDefinition function : contract.getFunctions()) {
            Method method = mcpMethods.get(function.getName());
            if (method == null) {
                continue;
            }
            rejectStreaming(serviceIdentifier, function.getName(), method);

            McpTool annotation = method.getAnnotation(McpTool.class);
            attachDecorator(function, annotation);

            String toolName = toolName(serviceIdentifier, function.getName());
            if (!toolNames.add(toolName)) {
                throw new IllegalStateException("Duplicate MCP tool name '" + toolName + "' for service "
                                                + serviceIdentifier);
            }

            tools.add(new McpToolDefinition()
                              .setToolName(toolName)
                              .setDescription(annotation.description())
                              .setInputSchema(schemaGenerator.generateInputSchema(function, referenceResolver))
                              .setCri(serviceIdentifier.cri().raw())
                              .setFunctionName(function.getName())
                              .setParameterNames(parameterNames(function))
                              .setReadOnlyHint(annotation.readOnlyHint())
                              .setDestructiveHint(annotation.destructiveHint())
                              .setIdempotentHint(annotation.idempotentHint()));
        }

        return new ServiceDirectoryEntry()
                .setId(entryId(serviceIdentifier))
                .setServiceAddress(serviceIdentifier.cri().baseResource())
                .setNamespace(serviceIdentifier.namespace())
                .setName(serviceIdentifier.name())
                .setVersion(serviceIdentifier.version())
                .setZone(serviceIdentifier.zone())
                .setContract(contract)
                .setSourceVersion(sourceVersion)
                .setPublished(true)
                .setMcpExposed(true)
                .setMcpTools(tools);
    }

    private void rejectStreaming(ServiceIdentifier serviceIdentifier, String functionName, Method method) {
        ReactiveAdapter adapter = reactiveAdapterRegistry.getAdapter(method.getReturnType());
        if (adapter != null && adapter.isMultiValue()) {
            throw new IllegalStateException("@McpTool function '" + functionName + "' on service " + serviceIdentifier
                                            + " has a streaming return type, which MCP tools do not support");
        }
    }

    private void attachDecorator(FunctionDefinition function, McpTool annotation) {
        McpToolC3Decorator decorator = new McpToolC3Decorator()
                .setDescription(annotation.description())
                .setReadOnlyHint(annotation.readOnlyHint())
                .setDestructiveHint(annotation.destructiveHint())
                .setIdempotentHint(annotation.idempotentHint());

        List<C3Decorator> decorators = function.getDecorators() != null
                                       ? new ArrayList<>(function.getDecorators())
                                       : new ArrayList<>();
        decorators.add(decorator);
        function.setDecorators(decorators);
        function.setMetadata(Map.of("description", annotation.description()));
    }

    private Map<String, Method> mcpMethods(Class<?> serviceInterface) {
        Map<String, Method> ret = new HashMap<>();
        for (Method method : serviceInterface.getMethods()) {
            if (method.isAnnotationPresent(McpTool.class)) {
                ret.put(method.getName(), method);
            }
        }
        return ret;
    }

    private Map<String, ObjectC3Type> referenceResolver(NamespaceDefinition namespace) {
        Map<String, ObjectC3Type> resolver = new HashMap<>();
        for (ComplexC3Type type : namespace.getComplexC3Types()) {
            if (type instanceof ObjectC3Type objectType) {
                resolver.put(objectType.getQualifiedName(), objectType);
            }
        }
        return resolver;
    }

    private List<String> parameterNames(FunctionDefinition function) {
        List<String> names = new ArrayList<>();
        for (ParameterDefinition parameter : function.getParameters()) {
            names.add(parameter.getName());
        }
        return names;
    }

    /**
     * Encodes the service address and function name into an MCP tool name matching {@code ^[a-zA-Z0-9_-]{1,128}$}:
     * dots become {@code _} and the function is separated by {@code -}. Names are minted here, never parsed back apart.
     */
    private String toolName(ServiceIdentifier serviceIdentifier, String functionName) {
        String toolName = (serviceIdentifier.qualifiedName().replace('.', '_') + "-" + functionName)
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        if (toolName.isEmpty() || toolName.length() > 128) {
            throw new IllegalStateException("MCP tool name '" + toolName + "' for function '" + functionName
                                            + "' on service " + serviceIdentifier
                                            + " does not fit the required 1-128 character bound");
        }
        return toolName;
    }

    private String entryId(ServiceIdentifier serviceIdentifier) {
        // runtime capture is always SYSTEM scope, so the id is namespace + name with no scope parts to prepend
        String namespace = serviceIdentifier.namespace();
        String name = serviceIdentifier.name();
        String id = namespace != null && !namespace.isEmpty() ? namespace + "." + name : name;
        return id.toLowerCase();
    }

}
