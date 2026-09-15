package org.kinotic.auth.cedar;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.policy.PolicySet;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.compilers.CedarCompiler;
import org.kinotic.auth.parsers.PolicyExpressionParser;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production Cedar authorization service that calls the Cedar Rust engine
 * directly via JNI, bypassing the SDK's POJO serialization round-trip.
 * <p>
 * Usage:
 * <ol>
 *   <li>Call {@link #registerPolicy} at service registration time to compile
 *       and cache an ABAC expression as a Cedar policy for a given action.</li>
 *   <li>Call {@link #isAuthorized} on each request with an {@link AuthorizationRequest}
 *       carrying the raw JSON payload, parameter names, principal attributes, and action.</li>
 * </ol>
 * <p>
 * The request JSON is built in a single streaming pass — the raw argument array
 * is transformed to a named object inline, with no intermediate strings or POJO
 * allocation. Pre-serialized policy JSON is embedded directly via
 * {@link JsonGenerator#writeRawValue}.
 */
public class CedarAuthorizationService implements AuthorizationEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PRINCIPAL_TYPE = "User";
    private static final String RESOURCE_TYPE = "ServiceMethod";

    private final MethodHandle callCedarJNI;
    private final com.fasterxml.jackson.databind.ObjectWriter cedarWriter;

    /**
     * Cached pre-serialized policy JSON keyed by action name.
     */
    private final ConcurrentHashMap<String, String> policyCache = new ConcurrentHashMap<>();

    /**
     * Creates a new CedarAuthorizationService, loading the Cedar native library
     * and obtaining reflection handles to the JNI methods.
     *
     * @throws CedarInitializationException if the native library cannot be loaded
     *         or the JNI methods cannot be accessed
     */
    public CedarAuthorizationService() {
        try {
            // Force native library load
            new BasicAuthorizationEngine();

            // Access callCedarJNI via reflection
            Method callMethod = BasicAuthorizationEngine.class
                    .getDeclaredMethod("callCedarJNI", String.class, String.class);
            callMethod.setAccessible(true);
            this.callCedarJNI = MethodHandles.lookup().unreflect(callMethod);

            // Access CedarJson.objectWriter() for policy serialization
            Class<?> cedarJsonClass = Class.forName("com.cedarpolicy.CedarJson");
            Method objectWriterMethod = cedarJsonClass.getDeclaredMethod("objectWriter");
            objectWriterMethod.setAccessible(true);
            this.cedarWriter = (com.fasterxml.jackson.databind.ObjectWriter) objectWriterMethod.invoke(null);

        } catch (Exception e) {
            throw new CedarInitializationException("Failed to initialize Cedar JNI", e);
        }
    }

    /**
     * Registers an ABAC policy expression for a given action. The expression is
     * parsed, compiled to Cedar syntax, wrapped in a full Cedar policy, serialized
     * to JSON, and cached for fast evaluation.
     * <p>
     * This should be called once per service method at registration time.
     *
     * @param action         the action name (e.g., "placeOrder", "transferFunds")
     * @param expression     the ABAC policy expression (e.g., "participant.roles contains 'finance' and order.amount < 50000")
     * @throws CedarPolicyRegistrationException if the expression cannot be parsed or compiled
     */
    @Override
    public void registerPolicy(String action, String expression) {
        try {
            // Parse ABAC expression → AST → Cedar condition
            var ast = PolicyExpressionParser.parse(expression);
            String cedarCondition = CedarCompiler.compile(ast);

            // Build full Cedar policy text
            String cedarPolicy = """
                    permit(
                        principal,
                        action == Action::"%s",
                        resource is %s
                    ) when {
                        %s
                    };""".formatted(action, RESOURCE_TYPE, cedarCondition);

            // Parse via SDK (validates the policy) and serialize to JSON
            PolicySet policySet = PolicySet.parsePolicies(cedarPolicy);
            String policyJson = cedarWriter.writeValueAsString(policySet);

            policyCache.put(action, policyJson);

        } catch (Exception e) {
            throw new CedarPolicyRegistrationException(
                    "Failed to register Cedar policy for action '" + action + "': " + expression, e);
        }
    }

    /**
     * Evaluates an authorization request against a registered policy.
     * <p>
     * Builds the Cedar JNI request JSON in a single streaming pass: principal attributes are
     * embedded as raw JSON, the raw argument array is transformed to a named object inline, and
     * the pre-serialized policy JSON is spliced in directly.
     *
     * @param request the request to authorize
     * @return true if the request is allowed, false if denied
     * @throws CedarAuthorizationException if no policy is registered for the action, or evaluation fails
     */
    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        String policyJson = policyCache.get(request.action());
        if (policyJson == null) {
            throw new CedarAuthorizationException("No policy registered for action: " + request.action());
        }

        try {
            String requestJson = buildRequestJson(
                    request.principalId(), request.principalAttributesJson(),
                    request.action(),
                    request.argumentsJson(), request.parameterNames().toArray(new String[0]),
                    policyJson);

            String responseJson = (String) callCedarJNI.invoke("AuthorizationOperation", requestJson);
            return responseJson.contains("\"allow\"");

        } catch (Throwable e) {
            throw new CedarAuthorizationException("Cedar authorization failed for action: " + request.action(), e);
        }
    }

    /**
     * Returns true if a policy is registered for the given action.
     */
    @Override
    public boolean hasPolicy(String action) {
        return policyCache.containsKey(action);
    }

    /**
     * Removes a registered policy.
     */
    @Override
    public void removePolicy(String action) {
        policyCache.remove(action);
    }

    // ========== Internal JSON building ==========

    /**
     * Builds the full Cedar JNI request JSON in a single streaming pass.
     */
    private String buildRequestJson(String principalId, String principalAttrsJson,
                                    String action,
                                    String rawArgsPayload, String[] paramNames,
                                    String policyJson) throws Exception {

        var writer = new StringWriter(512);
        try (var gen = MAPPER.createGenerator(writer)) {
            gen.writeStartObject();

            // principal EUID
            gen.writeName("principal");
            writeEuidJson(gen, PRINCIPAL_TYPE, principalId);

            // action EUID
            gen.writeName("action");
            writeEuidJson(gen, "Action", action);

            // resource EUID
            gen.writeName("resource");
            writeEuidJson(gen, RESOURCE_TYPE, action);

            // context (empty for now)
            gen.writeName("context");
            gen.writeStartObject();
            gen.writeEndObject();

            // policies — pre-serialized, embedded as raw JSON
            gen.writeName("policies");
            gen.writeRawValue(policyJson);

            // entities
            gen.writeName("entities");
            gen.writeStartArray();

            // Principal entity
            writeEntityWithRawAttrs(gen, PRINCIPAL_TYPE, principalId, principalAttrsJson);

            // Resource entity — fused: transform raw array → named object inline
            gen.writeStartObject();
            gen.writeName("uid");
            writeUidObject(gen, RESOURCE_TYPE, action);
            gen.writeName("attrs");
            gen.writeStartObject();
            try (var argParser = MAPPER.createParser(rawArgsPayload)) {
                argParser.nextToken(); // START_ARRAY
                int i = 0;
                while (argParser.nextToken() != JsonToken.END_ARRAY) {
                    gen.writeName(paramNames[i]);
                    gen.copyCurrentStructure(argParser);
                    i++;
                }
            }
            gen.writeEndObject();
            writeEmptyParentsAndTags(gen);
            gen.writeEndObject();

            gen.writeEndArray();
            gen.writeEndObject();
        }
        return writer.toString();
    }

    /**
     * Writes an EntityUID in Cedar's __entity wrapper format.
     */
    private static void writeEuidJson(JsonGenerator gen, String type, String id) throws Exception {
        gen.writeStartObject();
        gen.writeName("__entity");
        gen.writeStartObject();
        gen.writeName("type");
        gen.writeString(type);
        gen.writeName("id");
        gen.writeString(id);
        gen.writeEndObject();
        gen.writeEndObject();
    }

    /**
     * Writes the uid object (without __entity wrapper, used inside entity definitions).
     */
    private static void writeUidObject(JsonGenerator gen, String type, String id) throws Exception {
        gen.writeStartObject();
        gen.writeName("type");
        gen.writeString(type);
        gen.writeName("id");
        gen.writeString(id);
        gen.writeEndObject();
    }

    /**
     * Writes a complete entity with raw JSON attributes embedded directly.
     */
    private static void writeEntityWithRawAttrs(JsonGenerator gen, String type, String id,
                                                 String attrsJson) throws Exception {
        gen.writeStartObject();
        gen.writeName("uid");
        writeUidObject(gen, type, id);
        gen.writeName("attrs");
        gen.writeRawValue(attrsJson);
        writeEmptyParentsAndTags(gen);
        gen.writeEndObject();
    }

    /**
     * Writes empty parents array and tags object (common entity suffix).
     */
    private static void writeEmptyParentsAndTags(JsonGenerator gen) throws Exception {
        gen.writeName("parents");
        gen.writeStartArray();
        gen.writeEndArray();
        gen.writeName("tags");
        gen.writeStartObject();
        gen.writeEndObject();
    }
}
