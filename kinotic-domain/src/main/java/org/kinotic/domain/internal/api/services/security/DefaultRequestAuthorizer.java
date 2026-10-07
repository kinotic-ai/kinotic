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
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.FunctionSpec;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * The {@link RequestAuthorizer} over the stores: a function's check is read from its contract in the service
 * directory and kept, the object it names is read from the request with a parse that stops at it, and the
 * engine answers for the caller, or for the owner a delegate acts for, from the platform's store for an
 * organization's members and the platform's own staff, and from the application's store for an application's
 * users. A platform operator or one of the platform's machines, whose scope names no organization, is checked
 * on the platform itself wherever a check names the caller's scope, and an application's user on its tenant,
 * or on the application when it has none. What is kept of a contract is dropped when the directory announces
 * the contract written again.
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
        vertx.eventBus().<String>consumer(ServiceDirectory.CONTRACT_CHANGED_ADDRESS, message -> forget(message.body()));
    }

    // every version of the entry is kept under its own key; a contract written again changes all of them
    private void forget(String entryId) {
        definitions.asMap().keySet().removeIf(key -> key.startsWith(entryId + "|"));
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body) {
        Validate.notNull(cri, "cri cannot be null");
        Validate.notNull(participant, "participant cannot be null");
        Future<Void> ret;
        if (participant instanceof ScopedParticipant scoped) {
            String store = participant instanceof ApplicationParticipant application
                    ? DomainUtil.authzApplicationId(application.getOrganizationId(), application.getApplicationId())
                    : PLATFORM;
            ret = spec(cri).compose(spec -> spec.check() == null
                    ? Future.succeededFuture()
                    : check(spec, scoped, store, contentType, body));
        } else {
            ret = Future.failedFuture(new AuthorizationException("No store answers for participant " + participant.getId()));
        }
        return ret;
    }

    private Future<FunctionSpec> spec(CRI cri) {
        ServiceDirectory directory = directoryProvider.getIfAvailable();
        Future<FunctionSpec> ret;
        if (directory == null) {
            ret = Future.succeededFuture(FunctionSpec.UNCHECKED);
        } else {
            // an entry is keyed as a registration keys it: the zone and the qualified name, never the scope
            String entryId = cri.hasZone() ? cri.zone() + "~" + cri.resourceName() : cri.resourceName();
            String key = entryId + "|" + cri.version();
            ret = KinoticUtil.toFuture(definitions.get(key, (k, executor) -> load(directory, entryId).toCompletionStage().toCompletableFuture()))
                             .map(functions -> cri.hasPath() ? functions.getOrDefault(cri.path().substring(1), FunctionSpec.UNCHECKED)
                                                             : FunctionSpec.UNCHECKED);
        }
        return ret;
    }

    // The specs of a service's functions by name; empty for a service the directory has no definition for
    private Future<Map<String, FunctionSpec>> load(ServiceDirectory directory, String entryId) {
        return directory.findEntry(entryId).map(entry -> {
            Map<String, FunctionSpec> ret = new HashMap<>();
            if (entry == null || entry.getServiceDefinition() == null) {
                log.debug("No definition covers {}; its functions are served unchecked", entryId);
            } else {
                for (FunctionDefinition function : entry.getServiceDefinition().getFunctions()) {
                    ret.put(function.getName(),
                            new FunctionSpec(function.findDecorator(AuthzCheckC3Decorator.class),
                                             function.getParameters().stream().map(ParameterDefinition::getName).toList()));
                }
            }
            return Map.copyOf(ret);
        });
    }

    private Future<Void> check(FunctionSpec spec, ScopedParticipant participant, String store, String contentType, byte[] body) {
        AuthzCheckC3Decorator check = spec.check();
        Future<Void> ret;
        try {
            Resource checked = scopeLevelOf(check, participant.getScope());
            String permissionResource = check.getPermissionResource();
            String permission = check.getPermission();
            // An application's rows are typed in its own store. A caller above every application, an organization's
            // member or a platform operator, holds the definition itself on the platform, so a reading permission
            // of its rows is the definition's can_view and any other its can_edit
            if (AuthzUtil.isTemplate(permissionResource) && participant.getScope().applicationId() == null) {
                checked = new Resource(AuthzUtil.ENTITY_DEFINITION_TYPE, permissionResource);
                permissionResource = AuthzUtil.ENTITY_DEFINITION_TYPE;
                permission = AuthzUtil.isReading(permission) ? AuthzUtil.CAN_VIEW : AuthzUtil.CAN_EDIT;
            }
            String resource = typeOf(checked.type(), spec, participant, contentType, body);
            String resourceId = authzIdOf(resource, resolve(checked.id(), spec, participant, contentType, body), participant.getScope());
            String permissionType = typeOf(permissionResource, spec, participant, contentType, body);
            RelationshipTuple relationship = new RelationshipTuple(DomainUtil.authzUser(participant),
                                                                   AuthzUtil.permissionName(permissionType, permission),
                                                                   AuthzUtil.object(resource, resourceId));
            Consistency consistency = check.isConsistent() ? Consistency.HIGHER_CONSISTENCY : Consistency.MINIMIZE_LATENCY;
            ret = stores.modelId(store)
                        .compose(modelId -> relationships.check(store, modelId, relationship, consistency))
                        .compose(allowed -> allowed
                                ? Future.succeededFuture()
                                : Future.failedFuture(new AuthorizationException("Not authorized: " + relationship.relation()
                                                                                         + " on " + relationship.object())));
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
    private static Resource scopeLevelOf(AuthzCheckC3Decorator check, ParticipantScope scope) {
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
    private static String authzIdOf(String type, String id, ParticipantScope scope) {
        try {
            return DomainUtil.authzId(type, scope.organizationId(), id);
        } catch (IllegalArgumentException e) {
            throw new AuthorizationException("The check names " + type + " " + id + ", which only a caller in an organization addresses");
        }
    }

    // A type a request names is an entity definition's id, whose rows are typed by the definition's name
    private String typeOf(String template, FunctionSpec spec, ScopedParticipant participant, String contentType, byte[] body) {
        String ret = resolve(template, spec, participant, contentType, body);
        return AuthzUtil.isTemplate(template) ? DomainUtil.entityTypeOf(ret) : ret;
    }

    private String resolve(String template, FunctionSpec spec, ScopedParticipant participant, String contentType, byte[] body) {
        String ret = template;
        for (String reference : AuthzUtil.templateReferences(template)) {
            String value = valueOf(reference, spec, participant, contentType, body);
            if (value == null || value.isEmpty()) {
                throw new AuthorizationException("The request names no " + reference + ", which its check needs");
            }
            ret = ret.replace("{" + reference + "}", value);
        }
        return ret;
    }

    private String valueOf(String reference, FunctionSpec spec, ScopedParticipant participant, String contentType, byte[] body) {
        String parameter = AuthzUtil.referencedParameter(reference);
        String ret;
        if (parameter == null) {
            ret = scopeValue(reference, participant.getScope());
        } else {
            int position = spec.parameters().indexOf(parameter);
            if (position < 0) {
                throw new AuthorizationException("The check names the parameter " + parameter + ", which the function does not have");
            }
            List<String> path = reference.length() > parameter.length()
                    ? Arrays.asList(reference.substring(parameter.length() + 1).split("\\."))
                    : List.of();
            ret = locate(contentType, body, parameter, position, path);
        }
        return ret;
    }

    private static String scopeValue(String reference, ParticipantScope scope) {
        return switch (reference) {
            case "@organizationId" -> scope.organizationId();
            case "@applicationId" -> scope.applicationId();
            case "@tenantId" -> scope.tenantId();
            default -> throw new AuthorizationException("The check names the unknown scope value " + reference);
        };
    }

    /**
     * The scalar at a parameter, or at a property path inside it, read from the body with a parse that stops
     * there: the element at the parameter's position of a positional body, the field of its name of a named one.
     */
    private String locate(String contentType, byte[] body, String parameter, int position, List<String> path) {
        if (body == null || body.length == 0) {
            throw new AuthorizationException("The request carries no body, in which its check names " + parameter);
        }
        boolean named = EventConstants.CONTENT_TYPE_NAMED_JSON.equals(contentType);
        if (!named && !EventConstants.CONTENT_TYPE_JSON.equals(contentType)) {
            throw new AuthorizationException("The request's body is " + contentType + ", in which no id can be located");
        }
        try (JsonParser parser = jsonMapper.createParser(body)) {
            JsonToken start = parser.nextToken();
            boolean found = named
                    ? start == JsonToken.START_OBJECT && field(parser, parameter)
                    : start == JsonToken.START_ARRAY && element(parser, position);
            for (int i = 0; found && i < path.size(); i++) {
                found = parser.currentToken() == JsonToken.START_OBJECT && field(parser, path.get(i));
            }
            return found && parser.currentToken() != null && parser.currentToken().isScalarValue()
                    ? parser.getValueAsString()
                    : null;
        }
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
