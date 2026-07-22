package org.kinotic.core.internal.api.directory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.converter.jsonschema.McpJsonSchemaGenerator;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StreamC3Type;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
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
 * The {@link ServiceDirectory}: publishes the contracts of services that opt in with
 * {@code @Publish(directory = true)} or expose an {@code @McpTool} function, keeps liveness verified against
 * cluster registrations, serves the directory queries, and deploys the {@link ServiceLivenessUpdater} as one HA
 * cluster singleton on startup. Storage is supplied by a {@link ServiceDirectoryStrategy}; the directory bean
 * exists only when a strategy bean does, so a deployment without one has no directory at all.
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
    private final McpJsonSchemaGenerator schemaGenerator;
    private final Ignite ignite;

    // Registrations arriving during startup are held here and published in ONE conversion session on
    // ApplicationReadyEvent, so model types shared between services are converted once per node
    private final Map<ServiceIdentifier, Class<?>> pendingRegistrations = new HashMap<>();
    private final Object registrationLock = new Object();
    private boolean startupComplete; // guarded by registrationLock

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
        this.schemaGenerator = new McpJsonSchemaGenerator(idlConverterFactory);
        this.ignite = igniteProvider.getIfAvailable();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        drainStartupRegistrations();
        deployLivenessSingleton();
    }

    @Override
    public void register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        if (!shouldPublishToDirectory(serviceInterface)) {
            return;
        }
        boolean queued;
        synchronized (registrationLock) {
            queued = !startupComplete;
            if (queued) {
                pendingRegistrations.put(serviceIdentifier, serviceInterface);
            }
        }
        if (!queued) {
            // a late registration (lazily created bean) cannot join a batch, publish it immediately.
            // The entry starts with online unset and its ACTIVE registration event may have fired before the
            // entry existed, so refresh from the verified cluster state after the upsert
            publishAllToDirectory(Map.of(serviceIdentifier, serviceInterface))
                    .thenCompose(v -> refreshOnline(serviceIdentifier))
                    .whenComplete((v, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to register service {} in the directory", serviceIdentifier, throwable);
                        }
                    });
        }
    }

    @Override
    public void unregister(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface) {
        if (!shouldPublishToDirectory(serviceInterface)) {
            return;
        }
        // this node leaving says nothing about other instances of the service — verify, never
        // write offline blindly
        refreshOnline(serviceIdentifier)
                .whenComplete((v, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to refresh liveness for unregistered service {}", serviceIdentifier, throwable);
                    }
                });
    }

    private void drainStartupRegistrations() {
        Map<ServiceIdentifier, Class<?>> batch;
        synchronized (registrationLock) {
            startupComplete = true;
            batch = new HashMap<>(pendingRegistrations);
            pendingRegistrations.clear();
        }
        if (!batch.isEmpty()) {
            try {
                // one reconcile corrects the liveness of every entry from a single cluster snapshot,
                // instead of one registration query per service
                publishAllToDirectory(batch).thenCompose(v -> reconcileLiveness())
                                 .whenComplete((v, throwable) -> {
                                     if (throwable != null) {
                                         log.error("Startup directory publish failed", throwable);
                                     }
                                 });
            } catch (Exception e) {
                log.error("Startup directory publish failed", e);
            }
        }
    }

    // Every node requests the deployment; Ignite elects a single host for it cluster-wide
    private void deployLivenessSingleton() {
        if (ignite == null) {
            log.error("Ignite is not available; the service liveness updater singleton will not be deployed! This means the service directory will never be updated.");
            return;
        }
        // ServiceLivenessUpdater is an Ignite Service that manages the liveness of services
        ignite.services().deployClusterSingleton(LIVENESS_SINGLETON_NAME, new ServiceLivenessUpdater());
    }

    /**
     * Converts and upserts entries for all given registrations in one conversion session, so model types shared
     * between services are converted once.
     */
    private CompletableFuture<Void> publishAllToDirectory(Map<ServiceIdentifier, Class<?>> registrations) {
        NamespaceDefinition namespace = schemaFactory.createForServices(registrations.values());
        Map<String, ObjectC3Type> referenceResolver = referenceResolver(namespace.getComplexC3Types());
        Map<String, ServiceDefinition> definitionsByQualifiedName = new HashMap<>();
        for (ServiceDefinition definition : namespace.getServices()) {
            definitionsByQualifiedName.put(definition.getQualifiedName(), definition);
        }

        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Map.Entry<ServiceIdentifier, Class<?>> registration : registrations.entrySet()) {
            try {
                ServiceDefinition definition = definitionsByQualifiedName.get(registration.getValue().getName());
                if (definition == null) {
                    // conversion failed, SchemaFactory omitted the service and logged the cause
                    continue;
                }
                writes.add(strategy.upsertEntry(buildEntry(registration.getKey(),
                                                           definition,
                                                           referenceResolver)));
            } catch (Exception e) {
                // one bad service (e.g. an invalid @McpTool name) must not block the rest of the directory
                log.error("Failed to publish service {} to the directory", registration.getKey(), e);
            }
        }
        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
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
        CompletableFuture<Void> ret;
        if (reportDebounce.getIfPresent(cri) != null) {
            ret = CompletableFuture.completedFuture(null);
        } else {
            reportDebounce.put(cri, Boolean.TRUE);
            // a report is an invalidation trigger, not a value — verifyLiveness writes the verified state
            ret = verifyLiveness(CRI.create(cri).baseResource());
        }
        return ret;
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
                              .thenCompose(online -> strategy.setOnline(serviceIdentifier.qualifiedName(),
                                                                          online,
                                                                          Instant.now()));
    }

    private ServiceDirectoryEntry buildEntry(ServiceIdentifier serviceIdentifier,
                                             ServiceDefinition serviceDefinition,
                                             Map<String, ObjectC3Type> referenceResolver) {
        List<McpToolDefinition> tools = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();

        // tool-ness is carried by the C3 contract: SchemaFactory attached the decorator during conversion
        for (FunctionDefinition function : serviceDefinition.getFunctions()) {

            McpToolC3Decorator decorator = function.findDecorator(McpToolC3Decorator.class);
            if (decorator != null) {

                if (function.getReturnType() instanceof StreamC3Type) {
                    throw new IllegalStateException("@McpTool function '" + function.getName() + "' on service " + serviceIdentifier
                                                            + " has a streaming return type, which MCP tools do not support");
                }

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
        }

        return new ServiceDirectoryEntry()
                .setId(serviceIdentifier.qualifiedName())
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

    // Directory inclusion is opt-in via @Publish(directory = true); an @McpTool function is already
    // explicit intent to expose the service, so it implies inclusion
    private boolean shouldPublishToDirectory(Class<?> serviceInterface) {
        Publish publish = AnnotationUtils.findAnnotation(serviceInterface, Publish.class);
        boolean ret = publish != null && publish.directory();
        if (!ret) {
            for (Method method : serviceInterface.getMethods()) {
                if (method.isAnnotationPresent(McpTool.class)) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    private Map<String, ObjectC3Type> referenceResolver(Set<ComplexC3Type> referencedTypes) {
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
