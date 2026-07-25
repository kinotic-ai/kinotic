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
import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.converter.jsonschema.McpJsonSchemaGenerator;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.C3Type;
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
import java.util.regex.Pattern;

/**
 * The {@link ServiceDirectory}: publishes the contracts of services that opt in with
 * {@code @Publish(advertise = true)} or expose an {@code @McpTool} function, keeps liveness verified against
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

    // A strategy pattern is used, to favor composition over inheritance
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
                    .compose(v -> refreshOnline(serviceIdentifier))
                    .onFailure(throwable -> log.error("Failed to register service {} in the directory", serviceIdentifier, throwable));
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
                .onFailure(throwable -> log.error("Failed to refresh liveness for unregistered service {}", serviceIdentifier, throwable));
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
                publishAllToDirectory(batch).compose(v -> reconcileLiveness())
                                 .onFailure(throwable -> log.error("Startup directory publish failed", throwable));
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
    private Future<Void> publishAllToDirectory(Map<ServiceIdentifier, Class<?>> registrations) {
        NamespaceDefinition namespace = schemaFactory.createForServices(registrations.values());
        Map<String, ObjectC3Type> referenceResolver = referenceResolver(namespace.getComplexC3Types());
        Map<String, ServiceDefinition> definitionsByQualifiedName = new HashMap<>();
        for (ServiceDefinition definition : namespace.getServices()) {
            definitionsByQualifiedName.put(definition.getQualifiedName(), definition);
        }

        List<Future<Void>> writes = new ArrayList<>();
        for (Map.Entry<ServiceIdentifier, Class<?>> registration : registrations.entrySet()) {
            try {
                // the definition's qualified name is package + '.' + simpleName per the SchemaFactory
                // contract — never Class.getName(), which uses '$' for nested types
                Class<?> serviceInterface = registration.getValue();
                ServiceDefinition definition = definitionsByQualifiedName.get(
                        serviceInterface.getPackageName() + "." + serviceInterface.getSimpleName());
                if (definition == null) {
                    // conversion failed, SchemaFactory omitted the service and logged the cause
                    continue;
                }
                writes.add(strategy.upsertEntry(buildEntry(registration.getKey(),
                                                           serviceInterface,
                                                           definition,
                                                           referenceResolver)));
            } catch (Exception e) {
                // one bad service (e.g. an invalid @McpTool name) must not block the rest of the directory
                log.error("Failed to publish service {} to the directory", registration.getKey(), e);
            }
        }
        return Future.all(writes).mapEmpty();
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                              String applicationId,
                                                                              Pageable pageable) {
        return strategy.findEntriesScopedTo(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<McpToolDefinition>> findMcpToolsCallableBy(String organizationId,
                                                                             String applicationId,
                                                                             Pageable pageable) {
        return strategy.findMcpToolsCallableBy(organizationId, applicationId, pageable);
    }

    @Override
    public Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                                  String organizationId,
                                                                  String applicationId) {
        return strategy.findMcpToolByName(toolName, organizationId, applicationId);
    }

    @Override
    public Future<Void> reportUnreachable(String cri) {
        Future<Void> ret;
        if (reportDebounce.getIfPresent(cri) != null) {
            ret = Future.succeededFuture();
        } else {
            reportDebounce.put(cri, Boolean.TRUE);
            // a report is an invalidation trigger, not a value — verifyLiveness writes the verified state
            ret = verifyLiveness(CRI.create(cri).baseResource());
        }
        return ret;
    }

    @Override
    public Future<Void> verifyLiveness(String serviceAddress) {
        return eventBusService.isAnybodyListening(CRI.create(serviceAddress))
                              .compose(online -> strategy.setOnlineByAddress(serviceAddress, online, Instant.now()));
    }

    @Override
    public Future<Void> reconcileLiveness() {
        return eventBusService.activeServiceAddresses()
                              .compose(addresses -> strategy.reconcileLiveness(addresses, Instant.now()));
    }

    /**
     * Sets the entry's liveness to the verified cluster-wide registration state.
     */
    private Future<Void> refreshOnline(ServiceIdentifier serviceIdentifier) {
        return eventBusService.isAnybodyListening(serviceIdentifier.cri())
                              .compose(online -> strategy.setOnline(serviceIdentifier.qualifiedName(),
                                                                    online,
                                                                    Instant.now()));
    }

    private ServiceDirectoryEntry buildEntry(ServiceIdentifier serviceIdentifier,
                                             Class<?> serviceInterface,
                                             ServiceDefinition serviceDefinition,
                                             Map<String, ObjectC3Type> referenceResolver) {
        List<McpToolDefinition> tools = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();

        // tool-ness is carried by the C3 contract: SchemaFactory attached the decorator during conversion
        for (FunctionDefinition function : serviceDefinition.getFunctions()) {

            McpToolC3Decorator decorator = function.findDecorator(McpToolC3Decorator.class);
            if (decorator != null) {

                // A CompletableFuture<Flux<T>> converts to AsyncC3Type(StreamC3Type), so the stream
                // check must look through the async wrapper at the resolved value type
                C3Type returnType = function.getReturnType();
                if (returnType instanceof AsyncC3Type asyncC3Type) {
                    returnType = asyncC3Type.getValueType();
                }
                if (returnType instanceof StreamC3Type) {
                    throw new IllegalStateException("@McpTool function '" + function.getName() + "' on service " + serviceIdentifier
                                                            + " has a streaming return type, which MCP tools do not support");
                }

                String toolName = toolName(serviceIdentifier, function.getName());
                if (!toolNames.add(toolName)) {
                    throw new IllegalStateException("Duplicate MCP tool name '" + toolName + "' for service "
                                                            + serviceIdentifier);
                }

                tools.add(new McpToolDefinition()
                                  .setToolName(toolName)
                                  .setTitle(decorator.getTitle())
                                  .setDescription(decorator.getDescription())
                                  .setInputSchema(schemaGenerator.generateInputSchema(function, referenceResolver))
                                  // the full invocation CRI, so dispatching a call needs no reconstruction;
                                  // no version: the invoker does not support version-specific routing
                                  .setCri(CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME,
                                                     serviceIdentifier.scope(),
                                                     serviceIdentifier.qualifiedName(),
                                                     "/" + function.getName(),
                                                     null).raw())
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
                .setAdvertised(isAdvertised(serviceInterface))
                .setMcpExposed(!tools.isEmpty())
                .setMcpTools(tools.isEmpty() ? null : tools);
    }

    // Directory inclusion is opt-in via @Publish(advertise = true); an @McpTool function is already
    // explicit intent to expose the service, so it implies inclusion
    private boolean shouldPublishToDirectory(Class<?> serviceInterface) {
        return isAdvertised(serviceInterface) || hasMcpToolFunction(serviceInterface);
    }

    private boolean isAdvertised(Class<?> serviceInterface) {
        Publish publish = AnnotationUtils.findAnnotation(serviceInterface, Publish.class);
        return publish != null && publish.advertise();
    }

    private boolean hasMcpToolFunction(Class<?> serviceInterface) {
        boolean ret = false;
        for (Method method : serviceInterface.getMethods()) {
            if (method.isAnnotationPresent(McpTool.class)) {
                ret = true;
                break;
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
     * Returns the tool name for the function: the service's qualified name and the function name encoded to fit
     * {@code ^[a-zA-Z0-9_-]{1,128}$} (dots become {@code _}, the function is separated by {@code -}). The
     * qualified name makes the tool name unique system wide, and its zone prefix carries the organization and
     * application ids for every customer service. Names are minted here, never parsed back apart.
     */
    private String toolName(ServiceIdentifier serviceIdentifier, String functionName) {
        String toolName = (serviceIdentifier.qualifiedName().replace('.', '_') + "-" + functionName)
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        // a qualified name deep enough to overflow the 128-char limit must fail loudly, never truncate
        if (!TOOL_NAME_PATTERN.matcher(toolName).matches()) {
            throw new IllegalStateException("MCP tool name '" + toolName + "' for function '" + functionName
                                            + "' on service " + serviceIdentifier
                                            + " does not match the required pattern " + TOOL_NAME_PATTERN.pattern());
        }
        return toolName;
    }

}
