package org.kinotic.core.internal.api.directory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.annotations.Publish;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolAnnotations;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzUnchecked;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.converter.jsonschema.McpJsonSchemaGenerator;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.SchemaServiceFactory;
import org.kinotic.idl.api.utils.IdlUtil;
import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StreamC3Type;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link ServiceDirectory}: publishes the contracts of services that opt in with
 * {@code @Publish(advertise = true)}, expose an {@code @McpTool} function or declare an {@code @AuthzResource},
 * keeps liveness verified against cluster registrations, serves the directory queries, and deploys the
 * {@link ServiceLivenessUpdater} as one HA cluster singleton on startup. Services registered while the
 * context starts are converted in one session once every singleton exists, so a service whose contract cannot
 * be created fails the refresh, and their entries are stored on {@link ApplicationReadyEvent}. Storage is
 * supplied by a {@link ServiceDirectoryStrategy}; the directory bean exists only when a strategy bean does, so
 * a deployment without one has no directory at all.
 */
@Slf4j
@Component
// Evaluated at scan time: a module contributing a strategy must register its definitions before core's
// scan runs — KinoticDomainAutoConfiguration declares before = KinoticCoreAutoConfiguration for this
@ConditionalOnBean(ServiceDirectoryStrategy.class)
public class DefaultServiceDirectory implements ServiceDirectory, SmartInitializingSingleton {

    private static final String LIVENESS_SINGLETON_NAME = "kinotic-service-liveness-updater";
    // the platform publishes a few dozen services, so its definitions are read in a page or two
    private static final int DEFINITIONS_PAGE_SIZE = 200;

    // A strategy pattern is used, to favor composition over inheritance
    private final ServiceDirectoryStrategy strategy;
    private final EventBusService eventBusService;
    private final SchemaService schemaService;
    private final McpJsonSchemaGenerator schemaGenerator;
    private final Ignite ignite;
    private final Vertx vertx;

    // Registrations arriving while singletons are created are held here and converted in ONE conversion
    // session once all of them exist, so model types shared between services are converted once per node
    private final Map<ServiceIdentifier, ServiceDeclaration> pendingRegistrations = new HashMap<>(); // guarded by registrationLock
    // The entries of that session, held until ApplicationReadyEvent so one liveness reconcile from a single
    // cluster snapshot covers them all
    private final Map<ServiceIdentifier, ServiceDirectoryEntry> pendingEntries = new HashMap<>(); // guarded by registrationLock
    // Identifiers this node has published or queued, so unregister(ServiceIdentifier) knows whether
    // liveness needs a refresh without the caller re-supplying the registration classes
    private final Set<ServiceIdentifier> registered = new HashSet<>(); // guarded by registrationLock
    private final Object registrationLock = new Object();
    private boolean startupComplete; // guarded by registrationLock

    // Collapses repeated NO_HANDLERS reports for the same service into one verification (seconds).
    private final Cache<String, Boolean> reportDebounce = Caffeine.newBuilder()
                                                                  .expireAfterWrite(Duration.ofSeconds(5))
                                                                  .build();

    public DefaultServiceDirectory(ServiceDirectoryStrategy strategy,
                                   EventBusService eventBusService,
                                   SchemaServiceFactory schemaServiceFactory,
                                   IdlConverterFactory idlConverterFactory,
                                   Ignite ignite,
                                   Vertx vertx) {
        this.strategy = strategy;
        this.eventBusService = eventBusService;
        // a Participant is bound from the security context, never from the request, the rule
        // AbstractJacksonSupport and NamedJsonArgumentResolver bind by, so no contract advertises one
        this.schemaService = schemaServiceFactory.create(Set.of(Participant.class));
        this.schemaGenerator = new McpJsonSchemaGenerator(idlConverterFactory);
        this.ignite = ignite;
        this.vertx = vertx;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // held from the snapshot to the entries, so an unregister cannot find a registration in neither map;
        // a service whose contract cannot be created throws here and fails the refresh, so the server never
        // comes up serving a function the directory does not describe
        synchronized (registrationLock) {
            startupComplete = true;
            pendingEntries.putAll(buildEntries(pendingRegistrations));
            pendingRegistrations.clear();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        storeStartupEntries();
        deployLivenessSingleton();
    }

    @Override
    public void register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface, Class<?> serviceImplementation) {
        // the user class, so an AOP proxy never becomes the naming source
        ServiceDeclaration declaration = new ServiceDeclaration(serviceInterface, ClassUtils.getUserClass(serviceImplementation));
        if (!shouldPublishToDirectory(declaration)) {
            return;
        }
        boolean queued;
        synchronized (registrationLock) {
            queued = !startupComplete;
            if (queued) {
                registered.add(serviceIdentifier);
                pendingRegistrations.put(serviceIdentifier, declaration);
            }
        }
        if (!queued) {
            // a late registration (lazily created bean) cannot join the startup session, so it converts on its
            // own, throwing to the caller when its contract cannot be created, and is stored immediately.
            // The entry starts with online unset and its ACTIVE registration event may have fired before the
            // entry existed, so refresh from the verified cluster state after the upsert
            ServiceDirectoryEntry entry = buildEntries(Map.of(serviceIdentifier, declaration)).get(serviceIdentifier);
            synchronized (registrationLock) {
                registered.add(serviceIdentifier);
            }
            strategy.upsertEntry(entry)
                    .onSuccess(v -> announce(entry.getId()))
                    .compose(v -> refreshOnline(serviceIdentifier))
                    .onFailure(throwable -> log.error("Failed to register service {} in the directory", serviceIdentifier, throwable));
        }
    }

    @Override
    public void unregister(ServiceIdentifier serviceIdentifier) {
        boolean published;
        synchronized (registrationLock) {
            // a registration still pending never reached the directory, removing it from the batch is enough
            published = registered.remove(serviceIdentifier)
                    && pendingRegistrations.remove(serviceIdentifier) == null
                    && pendingEntries.remove(serviceIdentifier) == null;
        }
        if (published) {
            // this node leaving says nothing about other instances of the service — verify, never
            // write offline blindly
            refreshOnline(serviceIdentifier)
                    .onFailure(throwable -> log.error("Failed to refresh liveness for unregistered service {}", serviceIdentifier, throwable));
        }
    }

    private void storeStartupEntries() {
        Map<ServiceIdentifier, ServiceDirectoryEntry> batch;
        synchronized (registrationLock) {
            batch = new HashMap<>(pendingEntries);
            pendingEntries.clear();
        }
        if (!batch.isEmpty()) {
            try {
                // one cluster snapshot answers the liveness of every entry this node published, instead of one
                // registration query per service, and only this node's entries are written. The stamp is taken
                // before the snapshot, so an observation that starts later always lands after this one
                Instant observed = Instant.now();
                upsertAll(batch.values())
                        .compose(v -> eventBusService.activeServiceAddresses())
                        .compose(addresses -> {
                            Set<String> services = servicesOf(addresses);
                            List<Future<Void>> writes = new ArrayList<>();
                            for (ServiceDirectoryEntry entry : batch.values()) {
                                writes.add(strategy.setOnline(entry.getId(), services.contains(entry.getServiceAddress()), observed));
                            }
                            return Future.all(writes).mapEmpty();
                        })
                        .onFailure(throwable -> log.error("Startup directory publish failed", throwable));
            } catch (Exception e) {
                log.error("Startup directory publish failed", e);
            }
        }
    }

    // Every node requests the deployment; Ignite elects a single host for it cluster-wide
    private void deployLivenessSingleton() {
        // ServiceLivenessUpdater is an Ignite Service that manages the liveness of services
        ignite.services().deployClusterSingleton(LIVENESS_SINGLETON_NAME, new ServiceLivenessUpdater());
    }

    /**
     * Converts the given registrations in one conversion session, so model types shared between services are
     * converted once, and builds the entry of each.
     *
     * @throws IllegalStateException when a service's contract cannot be created, or a function's tool
     *                               declaration is one MCP cannot serve
     */
    private Map<ServiceIdentifier, ServiceDirectoryEntry> buildEntries(Map<ServiceIdentifier, ServiceDeclaration> registrations) {
        NamespaceDefinition namespace = schemaService.createForServices(registrations.values());
        Map<String, ObjectC3Type> referenceResolver = referenceResolver(namespace.getComplexC3Types());
        Map<String, ServiceDefinition> definitionsByQualifiedName = new HashMap<>();
        for (ServiceDefinition definition : namespace.getServices()) {
            definitionsByQualifiedName.put(definition.getQualifiedName(), definition);
        }
        Map<ServiceIdentifier, ServiceDirectoryEntry> ret = new HashMap<>();
        for (Map.Entry<ServiceIdentifier, ServiceDeclaration> registration : registrations.entrySet()) {
            // the definition's qualified name is package + '.' + simpleName per the SchemaService contract,
            // never Class.getName(), which uses '$' for nested types
            Class<?> serviceInterface = registration.getValue().serviceInterface();
            ServiceDefinition definition = definitionsByQualifiedName.get(
                    serviceInterface.getPackageName() + "." + serviceInterface.getSimpleName());
            ret.put(registration.getKey(), buildEntry(registration.getKey(), serviceInterface, definition, referenceResolver));
        }
        return ret;
    }

    private Future<Void> upsertAll(Collection<ServiceDirectoryEntry> entries) {
        List<Future<Void>> writes = new ArrayList<>();
        for (ServiceDirectoryEntry entry : entries) {
            writes.add(strategy.upsertEntry(entry).onSuccess(v -> announce(entry.getId())));
        }
        return Future.all(writes).mapEmpty();
    }

    // The entry's write is refreshed before it returns, so a node reading the contract on this announcement
    // reads the one written
    private void announce(String entryId) {
        vertx.eventBus().publish(ServiceDirectory.CONTRACT_CHANGED_ADDRESS, entryId);
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                   String applicationId,
                                                                   Pageable pageable) {
        return strategy.findEntriesScopedTo(organizationId, applicationId, pageable);
    }

    @Override
    public Future<ServiceDirectoryEntry> findEntry(String entryId) {
        return strategy.findEntry(entryId);
    }

    @Override
    public Future<Void> register(ServiceDirectoryEntry entry) {
        Validate.notNull(entry, "entry cannot be null");
        Validate.notBlank(entry.getOrganizationId(), "The entry names no organization");
        Validate.notBlank(entry.getApplicationId(), "The entry names no application");
        Validate.notBlank(entry.getZone(), "The entry names no zone");
        Validate.notNull(entry.getServiceDefinition(), "The entry carries no service definition");
        ServiceDefinition declared = entry.getServiceDefinition();
        // a runtime serves a service published in no namespace at <zone>~<Name>, so the zone is its namespace,
        // which keeps its qualified name apart from every other application's
        boolean inZoneNamespace = declared.getNamespace() == null || declared.getNamespace().isBlank();
        if (inZoneNamespace) {
            declared.setNamespace(entry.getZone());
        }
        ServiceDefinition definition = schemaService.deriveChecks(declared);
        // addressed as the runtime registers it: the zone and the qualified name, no scope, whatever its version,
        // with the zone written once for a service in the zone's namespace
        String entryId = entry.getZone() + ZoneUtil.ZONE_DELIMITER + (inZoneNamespace ? definition.getName() : definition.getQualifiedName());
        String serviceAddress = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, null, entryId, null, null).baseResource();
        // the runtime declares its types inline, so no reference resolves
        List<McpToolDefinition> tools = toolsOf(entryId, null, definition, Map.of());
        ServiceDirectoryEntry stored = new ServiceDirectoryEntry()
                .setId(entryId)
                .setServiceAddress(serviceAddress)
                .setOrganizationId(entry.getOrganizationId())
                .setApplicationId(entry.getApplicationId())
                .setProjectId(entry.getProjectId())
                .setNamespace(definition.getNamespace())
                .setName(definition.getName())
                .setVersion(entry.getVersion())
                .setZone(entry.getZone())
                .setDescription(entry.getDescription())
                .setServiceDefinition(definition)
                .setAdvertised(entry.isAdvertised())
                .setMcpExposed(!tools.isEmpty())
                .setMcpTools(tools.isEmpty() ? null : tools);
        return strategy.upsertEntry(stored)
                       .onSuccess(v -> announce(entryId))
                       .compose(v -> refreshOnline(entryId, serviceAddress));
    }

    @Override
    public Future<List<ServiceDefinition>> findSystemDefinitions() {
        return systemDefinitions(0, new ArrayList<>());
    }

    private Future<List<ServiceDefinition>> systemDefinitions(int pageNumber, List<ServiceDefinition> collected) {
        return strategy.findSystemEntries(Pageable.create(pageNumber, DEFINITIONS_PAGE_SIZE, Sort.by("id")))
                       .compose(page -> {
                           for (ServiceDirectoryEntry entry : page.getContent()) {
                               collected.add(entry.getServiceDefinition());
                           }
                           return page.getContent().size() < DEFINITIONS_PAGE_SIZE
                                   ? Future.succeededFuture(collected)
                                   : systemDefinitions(pageNumber + 1, collected);
                       });
    }

    @Override
    public Future<List<ServiceDefinition>> findApplicationDefinitions(String organizationId, String applicationId) {
        return applicationDefinitions(organizationId, applicationId, 0, new ArrayList<>());
    }

    private Future<List<ServiceDefinition>> applicationDefinitions(String organizationId, String applicationId, int pageNumber, List<ServiceDefinition> collected) {
        return strategy.findEntriesScopedTo(organizationId, applicationId, Pageable.create(pageNumber, DEFINITIONS_PAGE_SIZE, Sort.by("id")))
                       .compose(page -> {
                           for (ServiceDirectoryEntry entry : page.getContent()) {
                               if (entry.getServiceDefinition() != null) {
                                   collected.add(entry.getServiceDefinition());
                               }
                           }
                           return page.getContent().size() < DEFINITIONS_PAGE_SIZE
                                   ? Future.succeededFuture(collected)
                                   : applicationDefinitions(organizationId, applicationId, pageNumber + 1, collected);
                       });
    }

    @Override
    public Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                                String applicationId,
                                                                CursorPageable pageable) {
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
        String service = serviceOf(cri);
        Future<Void> ret;
        if (reportDebounce.getIfPresent(service) != null) {
            ret = Future.succeededFuture();
        } else {
            reportDebounce.put(service, Boolean.TRUE);
            // a report is an invalidation trigger, not a value — verifyLiveness writes the verified state
            ret = verifyLiveness(service);
        }
        return ret;
    }

    @Override
    public Future<Void> verifyLiveness(String serviceAddress) {
        String service = serviceOf(serviceAddress);
        // every observation is stamped with the time it starts, here and in refreshOnline, reconcileLiveness and
        // the startup publish: the fence on the entry declines a stamp earlier than the one it holds, so an
        // observation that started earlier never overwrites one that started later, however long its read took
        Instant observed = Instant.now();
        return anyInstanceListening(service)
                .compose(online -> strategy.setOnlineByAddress(service, online, observed));
    }

    @Override
    public Future<Void> reconcileLiveness() {
        Instant observed = Instant.now();
        return eventBusService.activeServiceAddresses()
                              .compose(addresses -> strategy.reconcileLiveness(servicesOf(addresses), observed));
    }

    /**
     * Sets the entry's liveness to the verified cluster-wide registration state.
     */
    private Future<Void> refreshOnline(ServiceIdentifier serviceIdentifier) {
        return refreshOnline(serviceIdentifier.qualifiedName(), serviceIdentifier.unscopedCri().baseResource());
    }

    private Future<Void> refreshOnline(String entryId, String serviceAddress) {
        Instant observed = Instant.now();
        return anyInstanceListening(serviceAddress).compose(online -> strategy.setOnline(entryId, online, observed));
    }

    // A service is reachable while any instance of it listens, on its shared address or under a scope, so
    // liveness is observed on the service address, the address with the scope removed
    private Future<Boolean> anyInstanceListening(String serviceAddress) {
        return eventBusService.activeServiceAddresses()
                              .map(addresses -> servicesOf(addresses).contains(serviceAddress));
    }

    private static Set<String> servicesOf(Set<String> addresses) {
        Set<String> ret = new HashSet<>();
        for (String address : addresses) {
            ret.add(serviceOf(address));
        }
        return ret;
    }

    // The service address of any address of a service: its base resource with the scope removed
    private static String serviceOf(String address) {
        CRI cri = CRI.create(address);
        String ret;
        if (cri.hasScope()) {
            String qualifiedName = cri.hasZone() ? cri.zone() + "~" + cri.resourceName() : cri.resourceName();
            ret = CRI.create(cri.scheme(), null, qualifiedName).baseResource();
        } else {
            ret = cri.baseResource();
        }
        return ret;
    }

    /**
     * Builds the entry of a converted service, with every tool its contract exposes.
     *
     * @throws IllegalStateException when a function's tool declaration is one MCP cannot serve
     */
    private ServiceDirectoryEntry buildEntry(ServiceIdentifier serviceIdentifier,
                                             Class<?> serviceInterface,
                                             ServiceDefinition serviceDefinition,
                                             Map<String, ObjectC3Type> referenceResolver) {
        List<McpToolDefinition> tools = toolsOf(serviceIdentifier.qualifiedName(),
                                                serviceIdentifier.scope(),
                                                serviceDefinition,
                                                referenceResolver);
        return new ServiceDirectoryEntry()
                .setId(serviceIdentifier.qualifiedName())
                .setServiceAddress(serviceIdentifier.unscopedCri().baseResource())
                .setNamespace(serviceIdentifier.namespace())
                .setName(serviceIdentifier.name())
                .setVersion(serviceIdentifier.version())
                .setZone(serviceIdentifier.zone())
                .setServiceDefinition(serviceDefinition)
                .setAdvertised(isAdvertised(serviceInterface))
                .setMcpExposed(!tools.isEmpty())
                .setMcpTools(tools.isEmpty() ? null : tools);
    }

    /**
     * The MCP tools of a service's functions, one per function carrying the tool decorator, named and
     * addressed under the service's qualified name.
     *
     * @throws IllegalStateException when a tool's function streams its result, or two tools share a name
     */
    private List<McpToolDefinition> toolsOf(String qualifiedName,
                                            String scope,
                                            ServiceDefinition serviceDefinition,
                                            Map<String, ObjectC3Type> referenceResolver) {
        List<McpToolDefinition> tools = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();

        // tool-ness is carried by the C3 contract: SchemaService attached the decorator during conversion
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
                    throw new IllegalStateException("@McpTool function '" + function.getName() + "' on service " + qualifiedName
                                                            + " has a streaming return type, which MCP tools do not support");
                }

                String toolName = KinoticUtil.mcpToolName(qualifiedName, function.getName());
                if (!toolNames.add(toolName)) {
                    // the name is a hash, so it names nothing on its own — the function it was minted from
                    // is what a reader needs to act on this
                    throw new IllegalStateException("Duplicate MCP tool name '" + toolName + "' for function '"
                                                            + function.getName() + "' on service " + qualifiedName);
                }

                tools.add(new McpToolDefinition()
                                  .setName(toolName)
                                  .setTitle(decorator.getTitle())
                                  .setDescription(decorator.getDescription())
                                  .setInputSchema(schemaGenerator.generateInputSchema(function, referenceResolver))
                                  // the full invocation CRI, so dispatching a call needs no reconstruction;
                                  // no version: the invoker does not support version-specific routing
                                  .setCri(CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME,
                                                     scope,
                                                     qualifiedName,
                                                     "/" + function.getName(),
                                                     null).raw())
                                  .setAnnotations(new McpToolAnnotations()
                                                          .setReadOnlyHint(decorator.isReadOnlyHint())
                                                          .setDestructiveHint(decorator.isDestructiveHint())
                                                          .setIdempotentHint(decorator.isIdempotentHint())
                                                          .setOpenWorldHint(decorator.isOpenWorldHint())));
            }
        }
        return tools;
    }

    // Directory inclusion is opt-in via @Publish(advertise = true); an @McpTool function is already
    // explicit intent to expose the service, so it implies inclusion, and an @AuthzResource or @AuthzUnchecked
    // service must be in the directory for the gateway to find its checks or its mark
    private boolean shouldPublishToDirectory(ServiceDeclaration registration) {
        return isAdvertised(registration.serviceInterface())
                || hasMcpToolFunction(registration)
                || AnnotationUtils.findAnnotation(registration.serviceInterface(), AuthzResource.class) != null
                || AnnotationUtils.findAnnotation(registration.serviceInterface(), AuthzUnchecked.class) != null;
    }

    private boolean isAdvertised(Class<?> serviceInterface) {
        Publish publish = AnnotationUtils.findAnnotation(serviceInterface, Publish.class);
        return publish != null && publish.advertise();
    }

    private boolean hasMcpToolFunction(ServiceDeclaration registration) {
        // a type-level @McpTool marks every function a tool, so the interface alone decides
        boolean ret = AnnotationUtils.findAnnotation(registration.serviceInterface(), McpTool.class) != null;
        if (!ret) {
            for (Method method : IdlUtil.serviceFunctions(registration.serviceInterface()).values()) {
                // findAnnotation on the most specific method honors @McpTool declared on the interface method
                // or only on the implementation's override, matching DefaultSchemaService's discovery
                Method specificMethod = ClassUtils.getMostSpecificMethod(method, registration.serviceImplementation());
                if (AnnotationUtils.findAnnotation(specificMethod, McpTool.class) != null) {
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
                ObjectC3Type previous = resolver.putIfAbsent(objectType.getQualifiedName(), objectType);
                if (previous != null) {
                    // a reference carries only the qualified name, so two types under one name would make
                    // every resolution of it arbitrary
                    throw new IllegalStateException("Duplicate ObjectC3Type qualified name '"
                            + objectType.getQualifiedName() + "' in the converted namespace");
                }
            }
        }
        return resolver;
    }

}
