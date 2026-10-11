package org.kinotic.domain.internal.api.services.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.FunctionSpec;
import org.kinotic.domain.internal.api.model.ParameterReference;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * The {@link RequestAuthorizer} over the stores: a function's check is read from its contract in the service
 * directory and kept, the object it names is read from the request, from a client's positional body with a parse
 * that stops once it has read the object, or from a tool call's arguments by name, and the engine answers for the
 * caller. A function is served without the engine only when its contract marks it
 * unchecked; a function no contract covers, of a service the directory holds no definition for or one the
 * definition leaves out, is refused. The engine answers for the caller, or for the owner a delegate acts for, from the platform's store for an
 * organization's members and the platform's own staff, and from the application's store for an application's
 * users. A platform operator or one of the platform's machines, whose scope names no organization, is checked
 * on the platform itself wherever a check names the caller's scope, and an application's user on its tenant,
 * or on the application when it has none. A definition's rows are checked on the definition a request names: a
 * tenant's user on the definition within its tenant, an organization's member on the definition in the
 * platform's store. What is kept of a contract is dropped when the directory announces the contract written
 * again. A refusal is answered "Not authorized", followed by its reason when {@code kinotic.debug} is on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRequestAuthorizer implements RequestAuthorizer {

    // a service's contract is read from the directory once and kept; the version in a request's CRI makes a
    // redeployed service a new key, and the retention bounds how long a platform service's new check waits
    private static final Duration DEFINITION_RETENTION = Duration.ofMinutes(5);
    private static final int DEFINITION_CAPACITY = 10_000;

    // the scope levels a check can be made on, each named by the template of its id, innermost first: a caller
    // missing one is checked on the next, and one missing them all on the platform
    private static final List<Resource> SCOPE_LEVELS = List.of(new Resource(AuthzUtil.TENANT_TYPE, "{@tenantId}"),
                                                               new Resource(AuthzUtil.APPLICATION_TYPE, "{@applicationId}"),
                                                               new Resource(AuthzUtil.ORGANIZATION_TYPE, "{@organizationId}"));
    private static final Resource PLATFORM_LEVEL = new Resource(AuthzUtil.PLATFORM_TYPE, AuthzUtil.PLATFORM_OBJECT_ID);

    private final ObjectProvider<ServiceDirectory> directoryProvider;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;
    private final JsonMapper jsonMapper;
    private final Vertx vertx;
    private final SecurityExceptionFactory securityExceptions;
    private final AsyncCache<String, Map<String, FunctionSpec>> definitions = Caffeine.newBuilder()
                                                                                    .maximumSize(DEFINITION_CAPACITY)
                                                                                    .expireAfterWrite(DEFINITION_RETENTION)
                                                                                    .buildAsync();

    /**
     * Listens for the directory announcing a contract written, and drops what is kept of it, so a function's
     * check changes for this node as soon as the directory holds the change.
     */
    @PostConstruct
    void listenForContractChanges() {
        // TODO: remove this and similar cache eviction logic infavor of more advanced cache eviction done in the kinotic-persistence module
        vertx.eventBus().<String>consumer(ServiceDirectory.CONTRACT_CHANGED_ADDRESS, message -> forget(message.body()));
    }

    // every version of the entry is kept under its own key; a contract written again changes all of them
    private void forget(String entryId) {
        definitions.asMap().keySet().removeIf(key -> key.startsWith(entryId + "|"));
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body) {
        return authorize(cri, participant, reference -> located(contentType, body, reference));
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, ObjectNode arguments) {
        Validate.notNull(arguments, "arguments cannot be null");
        return authorize(cri, participant, reference -> named(arguments, reference));
    }

    // Authorizes the request, reading a parameter the check names with the given function
    private Future<Void> authorize(CRI cri, Participant participant, Function<ParameterReference, String> arguments) {
        Validate.notNull(cri, "cri cannot be null");
        Validate.notNull(participant, "participant cannot be null");
        Future<Void> ret;
        if (participant instanceof ScopedParticipant scoped) {
            String store = participant instanceof ApplicationParticipant application
                    ? DomainUtil.authzApplicationId(application.getOrganizationId(), application.getApplicationId())
                    : PLATFORM;
            ret = spec(cri).compose(spec -> spec.check() == null
                    ? Future.succeededFuture()
                    : check(spec, scoped, store, arguments));
        } else {
            ret = Future.failedFuture(securityExceptions.notAuthorized("No store answers for participant of type {} with id {}",
                                                                       participant.getClass().getSimpleName(), participant.getId()));
        }
        return ret;
    }

    private Future<FunctionSpec> spec(CRI cri) {
        ServiceDirectory directory = directoryProvider.getIfAvailable();
        // an entry is keyed as a registration keys it: the zone and the qualified name, never the scope
        String entryId = cri.hasZone() ? cri.zone() + "~" + cri.resourceName() : cri.resourceName();
        Future<FunctionSpec> ret;
        if (directory == null) {
            ret = Future.failedFuture(securityExceptions.notAuthorized("No service directory holds the contract of {}, so its functions are refused",
                                                                       entryId));
        } else if (!cri.hasPath()) {
            ret = Future.failedFuture(securityExceptions.notAuthorized("The request names no function of {}", entryId));
        } else {
            String key = entryId + "|" + cri.version();
            String function = cri.path().substring(1);
            ret = KinoticUtil.toFuture(definitions.get(key, (k, executor) -> load(directory, entryId).toCompletionStage().toCompletableFuture()))
                             .map(functions -> {
                                 FunctionSpec spec = functions.get(function);
                                 if (spec == null) {
                                     throw securityExceptions.notAuthorized("No contract covers {} of {}; a function is served with a check,"
                                                                                    + " or marked unchecked", function, entryId);
                                 }
                                 return spec;
                             });
        }
        return ret;
    }

    // The specs of a service's functions by name, the checked and the marked unchecked: a function the contract
    // leaves out or marks neither way is absent, as every function of a service the directory has no definition for
    private Future<Map<String, FunctionSpec>> load(ServiceDirectory directory, String entryId) {
        return directory.findEntry(entryId).map(entry -> {
            Map<String, FunctionSpec> ret = new HashMap<>();
            if (entry == null || entry.getServiceDefinition() == null) {
                log.warn("No definition covers {}; its functions are refused until one is registered", entryId);
            } else {
                for (FunctionDefinition function : entry.getServiceDefinition().getFunctions()) {
                    AuthzCheckC3Decorator check = function.findDecorator(AuthzCheckC3Decorator.class);
                    List<String> parameters = function.getParameters().stream().map(ParameterDefinition::getName).toList();
                    if (check == null) {
                        log.warn("The contract of {} neither checks {} nor marks it unchecked; it is refused", entryId, function.getName());
                    } else {
                        ret.put(function.getName(), check.isUnchecked()
                                ? FunctionSpec.UNCHECKED
                                : new FunctionSpec(check, referencesOf(check, parameters, entryId + "/" + function.getName())));
                    }
                }
            }
            return Map.copyOf(ret);
        });
    }

    // Where each parameter reference of the check's id is read from, resolved once per contract rather than per
    // request; a reference to the caller's scope names no parameter
    private static List<ParameterReference> referencesOf(AuthzCheckC3Decorator check, List<String> parameters, String function) {
        List<ParameterReference> ret = new ArrayList<>();
        for (String reference : AuthzUtil.templateReferences(check.getResourceId())) {
            String parameter = AuthzUtil.referencedParameter(reference);
            if (parameter != null) {
                int position = parameters.indexOf(parameter);
                // the derivation refuses a contract referencing a parameter the function lacks, so a stored one
                // that does is corrupt, and its service is refused rather than served with a check that cannot be made
                if (position < 0) {
                    throw new IllegalStateException("The contract of " + function + " references the parameter " + parameter
                                                            + ", which the function does not carry");
                }
                List<String> path = reference.length() > parameter.length()
                        ? Arrays.asList(reference.substring(parameter.length() + 1).split("\\."))
                        : List.of();
                ret.add(new ParameterReference(reference, parameter, position, path));
            }
        }
        return ret;
    }

    private Future<Void> check(FunctionSpec spec, ScopedParticipant participant, String store, Function<ParameterReference, String> arguments) {
        AuthzCheckC3Decorator check = spec.check();
        ParticipantScope scope = participant.getScope();
        Future<Void> ret;
        try {
            Resource checked = scopeLevelOf(check, scope);
            String resourceId = authzIdOf(checked.type(), resolve(checked.id(), spec, scope, arguments), scope);
            if (!AuthzUtil.isObjectId(resourceId)) {
                throw securityExceptions.notAuthorized("The request names the {} '{}', which names no resource", checked.type(), resourceId);
            }
            // A tenant's user is checked on the definition within its tenant, an object no store holds tuples for:
            // its two edges come with the check, the tenant's from the caller's scope and the definition's from the
            // request, and the model reaches it through a grant on the tenant only for a definition placed in the
            // application
            boolean withinTenant = AuthzUtil.ENTITY_DEFINITION_TYPE.equals(checked.type()) && scope.tenantId() != null;
            String object = withinTenant
                    ? AuthzUtil.object(AuthzUtil.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(resourceId, scope.tenantId()))
                    : AuthzUtil.object(checked.type(), resourceId);
            List<RelationshipTuple> edges = withinTenant ? DomainUtil.tenantDefinitionEdges(resourceId, scope.tenantId()) : List.of();
            RelationshipTuple relationship = new RelationshipTuple(DomainUtil.authzUser(participant),
                                                                   AuthzUtil.permissionName(check.getPermissionResource(), check.getPermission()),
                                                                   object);
            Consistency consistency = check.isConsistent() ? Consistency.HIGHER_CONSISTENCY : Consistency.MINIMIZE_LATENCY;
            ret = stores.modelId(store)
                        .compose(modelId -> relationships.check(store, modelId, relationship, consistency, edges))
                        .compose(allowed -> allowed
                                ? Future.succeededFuture()
                                : Future.failedFuture(securityExceptions.notAuthorized("{} on {} for {}", relationship.relation(),
                                                                                       relationship.object(), relationship.user())));
        } catch (AuthorizationException e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    /**
     * The object a check on the caller's scope is made on: the level the check names, or, for a caller whose
     * scope stops above it, the caller's own level, as an organization member asked for an application's
     * projects is checked on the organization, and a platform operator asked for either on the platform.
     * Every ancestor carries the permissions of the levels below it, so the check keeps its meaning there. A
     * check on anything but a scope level is kept as declared.
     */
    private Resource scopeLevelOf(AuthzCheckC3Decorator check, ParticipantScope scope) {
        Resource ret = new Resource(check.getResource(), check.getResourceId());
        int level = SCOPE_LEVELS.indexOf(ret);
        if (level >= 0) {
            while (level < SCOPE_LEVELS.size()
                    && scopeValue(AuthzUtil.templateReferences(SCOPE_LEVELS.get(level).id()).getFirst(), scope) == null) {
                level++;
            }
            ret = level < SCOPE_LEVELS.size() ? SCOPE_LEVELS.get(level) : PLATFORM_LEVEL;
        }
        return ret;
    }

    // An application or a project is named within the caller's organization, so a caller outside every
    // organization names none
    private String authzIdOf(String type, String id, ParticipantScope scope) {
        try {
            return DomainUtil.authzId(type, scope.organizationId(), id);
        } catch (IllegalArgumentException e) {
            throw securityExceptions.notAuthorized("The check names {} {}, which only a caller in an organization addresses",
                                                   type, id);
        }
    }

    private String resolve(String template, FunctionSpec spec, ParticipantScope scope, Function<ParameterReference, String> arguments) {
        String ret = template;
        for (String reference : AuthzUtil.templateReferences(template)) {
            ParameterReference parameter = spec.reference(reference);
            String value = parameter == null
                    ? scopeValue(reference, scope)
                    : arguments.apply(parameter);
            if (value == null || value.isEmpty()) {
                throw securityExceptions.notAuthorized("The request names no {}, which its check needs", reference);
            }
            ret = ret.replace("{" + reference + "}", value);
        }
        return ret;
    }

    private String scopeValue(String reference, ParticipantScope scope) {
        return switch (reference) {
            case "@organizationId" -> scope.organizationId();
            case "@applicationId" -> scope.applicationId();
            case "@tenantId" -> scope.tenantId();
            default -> throw securityExceptions.notAuthorized("The check names the unknown scope value {}", reference);
        };
    }

    /**
     * The scalar at a parameter, or at a property path inside it, read from a positional body with a parse that
     * stops once it has read the parameter: the element at the parameter's position, and the objects the path
     * descends into.
     *
     * @throws AuthorizationException when the body is not a positional JSON body, or an object the path descends
     *                                into names a property twice
     */
    private String located(String contentType, byte[] body, ParameterReference reference) {
        if (body == null || body.length == 0) {
            throw securityExceptions.notAuthorized("The request carries no body, in which its check names {}", reference.parameter());
        }
        if (!EventConstants.CONTENT_TYPE_JSON.equals(contentType)) {
            throw securityExceptions.notAuthorized("The request's body is {}, in which no id can be located", contentType);
        }
        String ret;
        // a service binds the last of a property named twice, which need not be the one read here, so the parser
        // refuses a repeated name and each object the path descends into is read to its end
        try (JsonParser parser = jsonMapper.reader().with(StreamReadFeature.STRICT_DUPLICATE_DETECTION).createParser(body)) {
            boolean found = parser.nextToken() == JsonToken.START_ARRAY && element(parser, reference.position());
            List<String> path = reference.path();
            for (int i = 0; found && i < path.size(); i++) {
                found = parser.currentToken() == JsonToken.START_OBJECT && field(parser, path.get(i));
            }
            ret = found && parser.currentToken() != null && parser.currentToken().isScalarValue()
                    ? parser.getValueAsString()
                    : null;
            if (ret != null) {
                finish(parser, path.size());
            }
        } catch (StreamReadException e) {
            throw securityExceptions.notAuthorized("The request's body cannot be read for its check: {}", e.getOriginalMessage());
        }
        return ret;
    }

    // Reads past the end of the given number of objects the parser is inside, the innermost first
    private static void finish(JsonParser parser, int objects) {
        int open = objects;
        JsonToken token = parser.currentToken();
        while (open > 0 && token != null) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT) {
                open--;
            } else if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                parser.skipChildren();
            }
        }
    }

    // The scalar at a parameter, or at a property path inside it, of a tool call's arguments
    private static String named(ObjectNode arguments, ParameterReference reference) {
        JsonNode node = arguments.get(reference.parameter());
        for (int i = 0; node != null && i < reference.path().size(); i++) {
            node = node.isObject() ? node.get(reference.path().get(i)) : null;
        }
        return node != null && node.isValueNode() && !node.isNull() ? node.asString() : null;
    }

    // Leaves the parser on the value of the named field of the object it has just entered, false when there is none
    private static boolean field(JsonParser parser, String name) {
        boolean ret = false;
        while (!ret && parser.nextToken() == JsonToken.PROPERTY_NAME) {
            if (name.equals(parser.currentName())) {
                parser.nextToken();
                ret = true;
            } else {
                parser.nextToken();
                parser.skipChildren();
            }
        }
        return ret;
    }

    // Leaves the parser on the element at the position of the array it has just entered, false when it is shorter
    private static boolean element(JsonParser parser, int position) {
        boolean ret = true;
        for (int i = 0; ret && i <= position; i++) {
            JsonToken token = parser.nextToken();
            if (token == null || token == JsonToken.END_ARRAY) {
                ret = false;
            } else if (i < position) {
                parser.skipChildren();
            }
        }
        return ret;
    }
}
