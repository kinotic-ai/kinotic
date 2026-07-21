package org.kinotic.core.internal.api.directory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.converter.jsonschema.McpJsonSchemaGenerator;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.directory.ServiceSchema;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The {@link ServiceDirectory}: captures published service contracts, keeps liveness verified against cluster
 * registrations, serves the directory queries, and deploys the {@link ServiceLivenessUpdater} as one HA cluster
 * singleton on startup. Storage is supplied by a {@link ServiceDirectoryStrategy}; the directory bean exists only
 * when a strategy bean does, so a deployment without one has no directory at all.
 */
@Slf4j
@Component
// Evaluated at scan time: a module contributing a strategy must register its definitions before core's
// scan runs — KinoticDomainAutoConfiguration declares before = KinoticCoreAutoConfiguration for this
@ConditionalOnBean(ServiceDirectoryStrategy.class)
public class DefaultServiceDirectory implements ServiceDirectory {

    private static final String LIVENESS_SINGLETON_NAME = "kinotic-service-liveness-updater";

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,128}$");

    private final ServiceDirectoryStrategy strategy;
    private final EventBusService eventBusService;
    private final SchemaFactory schemaFactory;
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    private final McpJsonSchemaGenerator schemaGenerator;
    private final Ignite ignite;

    // Collapses repeated NO_HANDLERS reports for the same CRI into one verification (seconds).
    private final Cache<String, Boolean> reportDebounce = Caffeine.newBuilder()
                                                                  .expireAfterWrite(Duration.ofSeconds(5))
                                                                  .build();

    public DefaultServiceDirectory(ServiceDirectoryStrategy strategy,
                                   EventBusService eventBusService,
                                   SchemaFactory schemaFactory,
                                   IdlConverterFactory idlConverterFactory,
                                   ObjectProvider<Ignite> igniteProvider) {
        this.strategy = strategy;
        this.eventBusService = eventBusService;
        this.schemaFactory = schemaFactory;
        this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
        this.schemaGenerator = new McpJsonSchemaGenerator(idlConverterFactory);
        this.ignite = igniteProvider.getIfAvailable();
    }

    // Every node requests the deployment; Ignite elects a single host for it cluster-wide
    @EventListener(ApplicationReadyEvent.class)
    public void deployLivenessSingleton() {
        if (ignite == null) {
            log.warn("Ignite is not available; the service liveness updater singleton will not be deployed");
            return;
        }
        // ServiceLivenessUpdater is an Ignite Service; its Spring dependencies are injected via
        // @SpringResource on the node Ignite elects to host it
        ignite.services().deployClusterSingleton(LIVENESS_SINGLETON_NAME, new ServiceLivenessUpdater());
    }

    @Override
    public CompletableFuture<Void> register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        // a new entry starts with online unset, and the ACTIVE registration event fired before the
        // entry existed — refresh from the verified cluster state after the upsert
        return strategy.upsertEntry(buildEntry(serviceIdentifier, serviceInterface))
                       .thenCompose(v -> refreshOnline(serviceIdentifier));
    }

    @Override
    public CompletableFuture<Void> unregister(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        // this node leaving says nothing about other instances of the service — verify, never
        // write offline blindly
        return refreshOnline(serviceIdentifier);
    }

    @Override
    public CompletableFuture<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                              String applicationId,
                                                                              Pageable pageable) {
        return strategy.findEntriesScopedTo(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Page<McpToolDefinition>> findMcpToolsCallableBy(String organizationId,
                                                                             String applicationId,
                                                                             Pageable pageable) {
        return strategy.findMcpToolsCallableBy(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Void> reportUnreachable(String cri) {
        if (reportDebounce.getIfPresent(cri) != null) {
            return CompletableFuture.completedFuture(null);
        }
        reportDebounce.put(cri, Boolean.TRUE);
        // a report is an invalidation trigger, not a value — verifyLiveness writes the verified state
        return verifyLiveness(CRI.create(cri).baseResource());
    }

    @Override
    public CompletableFuture<Void> verifyLiveness(String serviceAddress) {
        return eventBusService.isAnybodyListening(CRI.create(serviceAddress))
                              .toCompletionStage()
                              .toCompletableFuture()
                              .thenCompose(online -> strategy.setOnlineByAddress(serviceAddress,
                                                                                online,
                                                                                Instant.now()));
    }

    @Override
    public CompletableFuture<Void> reconcileLiveness() {
        return eventBusService.activeServiceAddresses()
                              .toCompletionStage()
                              .toCompletableFuture()
                              .thenCompose(addresses -> strategy.reconcileLiveness(addresses, Instant.now()));
    }

    /**
     * Sets the entry's liveness to the verified cluster-wide registration state.
     */
    private CompletableFuture<Void> refreshOnline(ServiceIdentifier serviceIdentifier) {
        return eventBusService.isAnybodyListening(serviceIdentifier.cri())
                              .toCompletionStage()
                              .toCompletableFuture()
                              .thenCompose(online -> strategy.setOnline(entryId(serviceIdentifier),
                                                                          online,
                                                                          Instant.now()));
    }

    private ServiceDirectoryEntry buildEntry(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        ServiceSchema schema = schemaFactory.createForService(serviceInterface);
        ServiceDefinition serviceDefinition = schema.serviceDefinition();
        Map<String, ObjectC3Type> referenceResolver = referenceResolver(schema.referencedTypes());
        Map<String, Method> methodsByName = methodsByName(serviceInterface);

        List<McpToolDefinition> tools = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();

        // tool-ness is carried by the C3 contract: SchemaFactory attached the decorator during conversion
        for (FunctionDefinition function : serviceDefinition.getFunctions()) {
            McpToolC3Decorator decorator = function.findDecorator(McpToolC3Decorator.class);
            if (decorator == null) {
                continue;
            }
            rejectStreaming(serviceIdentifier, function.getName(), methodsByName.get(function.getName()));

            String toolName = toolName(serviceIdentifier, function.getName(), decorator);
            if (!toolNames.add(toolName)) {
                throw new IllegalStateException("Duplicate MCP tool name '" + toolName + "' for service "
                                                + serviceIdentifier);
            }

            tools.add(new McpToolDefinition()
                              .setToolName(toolName)
                              .setDescription(decorator.getDescription())
                              .setInputSchema(schemaGenerator.generateInputSchema(function, referenceResolver))
                              .setCri(serviceIdentifier.cri().raw())
                              .setFunctionName(function.getName())
                              .setReadOnlyHint(decorator.isReadOnlyHint())
                              .setDestructiveHint(decorator.isDestructiveHint())
                              .setIdempotentHint(decorator.isIdempotentHint()));
        }

        return new ServiceDirectoryEntry()
                .setId(entryId(serviceIdentifier))
                .setServiceAddress(serviceIdentifier.cri().baseResource())
                .setNamespace(serviceIdentifier.namespace())
                .setName(serviceIdentifier.name())
                .setVersion(serviceIdentifier.version())
                .setZone(serviceIdentifier.zone())
                .setServiceDefinition(serviceDefinition)
                .setPublished(true)
                .setMcpExposed(!tools.isEmpty())
                .setMcpTools(tools.isEmpty() ? null : tools);
    }

    private String entryId(ServiceIdentifier serviceIdentifier) {
        // runtime capture is always SYSTEM scope, so the id is namespace + name with no scope parts to prepend
        String namespace = serviceIdentifier.namespace();
        String name = serviceIdentifier.name();
        String id = namespace != null && !namespace.isEmpty() ? namespace + "." + name : name;
        return id.toLowerCase();
    }

    private void rejectStreaming(ServiceIdentifier serviceIdentifier, String functionName, Method method) {
        ReactiveAdapter adapter = reactiveAdapterRegistry.getAdapter(method.getReturnType());
        if (adapter != null && adapter.isMultiValue()) {
            throw new IllegalStateException("@McpTool function '" + functionName + "' on service " + serviceIdentifier
                                            + " has a streaming return type, which MCP tools do not support");
        }
    }

    private Map<String, Method> methodsByName(Class<?> serviceInterface) {
        Map<String, Method> ret = new HashMap<>();
        for (Method method : serviceInterface.getMethods()) {
            ret.put(method.getName(), method);
        }
        return ret;
    }

    private Map<String, ObjectC3Type> referenceResolver(List<ComplexC3Type> referencedTypes) {
        Map<String, ObjectC3Type> resolver = new HashMap<>();
        for (ComplexC3Type type : referencedTypes) {
            if (type instanceof ObjectC3Type objectType) {
                resolver.put(objectType.getQualifiedName(), objectType);
            }
        }
        return resolver;
    }

    /**
     * Returns the tool name for the function: the decorator's explicit name when given, otherwise the service's
     * qualified name and the function name encoded to fit {@code ^[a-zA-Z0-9_-]{1,128}$} (dots become {@code _},
     * the function is separated by {@code -}). Names are minted here, never parsed back apart.
     */
    private String toolName(ServiceIdentifier serviceIdentifier, String functionName, McpToolC3Decorator decorator) {
        // an explicit name is used verbatim and rejected when invalid, never silently sanitized
        String toolName = decorator.getName() == null || decorator.getName().isEmpty()
                          ? (serviceIdentifier.qualifiedName().replace('.', '_') + "-" + functionName)
                                  .replaceAll("[^a-zA-Z0-9_-]", "_")
                          : decorator.getName();
        if (!TOOL_NAME_PATTERN.matcher(toolName).matches()) {
            throw new IllegalStateException("MCP tool name '" + toolName + "' for function '" + functionName
                                            + "' on service " + serviceIdentifier
                                            + " does not match the required pattern " + TOOL_NAME_PATTERN.pattern());
        }
        return toolName;
    }

}
