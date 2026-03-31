package org.kinotic.auth.cedar;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.policy.PolicySet;
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
 *   <li>Call {@link #isAuthorized} on each request with the raw JSON payload,
 *       parameter names, principal attributes JSON, and action name.</li>
 * </ol>
 * <p>
 * The request JSON is built in a single streaming pass — the raw argument array
 * is transformed to a named object inline, with no intermediate strings or POJO
 * allocation. Pre-serialized policy JSON is embedded directly via
 * {@link JsonGenerator#writeRawValue}.
 */
public class CedarAuthorizationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
     * @param resourceType   the Cedar resource type (e.g., "ServiceMethod")
     * @throws CedarPolicyRegistrationException if the expression cannot be parsed or compiled
     */
    public void registerPolicy(String action, String expression, String resourceType) {
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
                    };""".formatted(action, resourceType, cedarCondition);

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
     * Builds the Cedar JNI request JSON in a single streaming pass:
     * <ul>
     *   <li>Principal attributes are embedded as raw JSON</li>
     *   <li>The raw argument array is transformed to a named object inline</li>
     *   <li>The pre-serialized policy JSON is spliced in directly</li>
     * </ul>
     *
     * @param principalType     the Cedar principal type (e.g., "User")
     * @param principalId       the principal identifier (e.g., "user-123")
     * @param principalAttrsJson raw JSON for principal attributes (e.g., {"roles": ["finance"]})
     * @param action            the action name (must match a registered policy)
     * @param resourceType      the Cedar resource type (e.g., "ServiceMethod")
     * @param resourceId        the resource identifier for this request
     * @param rawArgsPayload    the raw JSON argument array (e.g., [{"amount": 25000}])
     * @param paramNames        the method parameter names to map array elements to
     * @return true if the request is allowed, false if denied
     * @throws CedarAuthorizationException if evaluation fails
     */
    public boolean isAuthorized(String principalType,
                                String principalId,
                                String principalAttrsJson,
                                String action,
                                String resourceType,
                                String resourceId,
                                String rawArgsPayload,
                                String[] paramNames) {
        String policyJson = policyCache.get(action);
        if (policyJson == null) {
            throw new CedarAuthorizationException("No policy registered for action: " + action);
        }

        try {
            String requestJson = buildRequestJson(
                    principalType, principalId, principalAttrsJson,
                    action,
                    resourceType, resourceId,
                    rawArgsPayload, paramNames,
                    policyJson);

            String responseJson = (String) callCedarJNI.invoke("AuthorizationOperation", requestJson);
            return responseJson.contains("\"allow\"");

        } catch (Throwable e) {
            throw new CedarAuthorizationException("Cedar authorization failed for action: " + action, e);
        }
    }

    /**
     * Returns true if a policy is registered for the given action.
     */
    public boolean hasPolicy(String action) {
        return policyCache.containsKey(action);
    }

    /**
     * Removes a registered policy.
     */
    public void removePolicy(String action) {
        policyCache.remove(action);
    }

    // ========== Internal JSON building ==========

    /**
     * Builds the full Cedar JNI request JSON in a single streaming pass.
     */
    private String buildRequestJson(String principalType, String principalId, String principalAttrsJson,
                                    String action,
                                    String resourceType, String resourceId,
                                    String rawArgsPayload, String[] paramNames,
                                    String policyJson) throws Exception {

        var writer = new StringWriter(512);
        try (var gen = MAPPER.createGenerator(writer)) {
            gen.writeStartObject();

            // principal EUID
            gen.writeName("principal");
            writeEuidJson(gen, principalType, principalId);

            // action EUID
            gen.writeName("action");
            writeEuidJson(gen, "Action", action);

            // resource EUID
            gen.writeName("resource");
            writeEuidJson(gen, resourceType, resourceId);

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
            writeEntityWithRawAttrs(gen, principalType, principalId, principalAttrsJson);

            // Resource entity — fused: transform raw array → named object inline
            gen.writeStartObject();
            gen.writeName("uid");
            writeUidObject(gen, resourceType, resourceId);
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
