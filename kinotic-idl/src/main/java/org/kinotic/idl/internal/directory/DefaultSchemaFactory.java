

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.directory.GenericTypeConverter;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.annotations.McpToolInfo;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.utils.IdlUtil;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides the ability to create {@link C3Type}'s
 *
 *
 * Created by navid on 2019-06-13.
 */
@Slf4j
@Component
public class DefaultSchemaFactory implements SchemaFactory {

    private final GenericTypeConverter typeConverter;
    // extracted Javadoc resources by type; a type without a resource caches an empty map
    private final Map<Class<?>, Map<String, String>> javadocCache = new ConcurrentHashMap<>();

    public DefaultSchemaFactory(GenericTypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    @Override
    public C3Type createForClass(Class<?> clazz) {
        DefaultConversionContext conversionContext = new DefaultConversionContext(typeConverter, false);
        return this.createForPojo(clazz, conversionContext);
    }

    private C3Type createForPojo(Class<?> clazz, ConversionContext conversionContext) {
        Assert.notNull(clazz, "Class cannot be null");
        Assert.notNull(conversionContext, "ConversionContext cannot be null");

        C3Type ret;
        ResolvableType resolvableType = ResolvableType.forClass(clazz);
        if(typeConverter.supports(resolvableType)){

            ret = typeConverter.convert(resolvableType, conversionContext);

        }else{
            throw new IllegalArgumentException("No schemaConverter can be found for "+ clazz.getName());
        }
        return ret;
    }

    @Override
    public NamespaceDefinition createForServices(Collection<ServiceDeclaration> services) {
        Assert.notNull(services, "services cannot be null");
        // one conversion context for the whole batch, so complex types shared between services convert once
        DefaultConversionContext conversionContext = new DefaultConversionContext(typeConverter, true);

        NamespaceDefinition ret = new NamespaceDefinition();
        // record equality collapses duplicates, so a service declared twice converts once
        for (ServiceDeclaration declaration : new LinkedHashSet<>(services)) {
            // a service with an unconvertible type is omitted so the rest of the batch still converts;
            // ObjectC3Types are cached only after converting completely, so a failure leaves no partial types
            try {
                ret.addServiceDefinition(createForService(declaration.serviceInterface(),
                                                          declaration.serviceImplementation(),
                                                          conversionContext));
            } catch (Exception e) {
                log.error("Failed to create ServiceDefinition for {}", declaration.serviceInterface().getName(), e);
            }
        }
        ret.setComplexC3Types(conversionContext.getComplexC3Types());
        return ret;
    }

    private ServiceDefinition createForService(Class<?> serviceInterface,
                                               Class<?> implementation,
                                               ConversionContext conversionContext) {
        Assert.notNull(serviceInterface, "serviceInterface cannot be null");
        Assert.notNull(implementation, "implementation cannot be null");

        ServiceDefinition serviceDefinition = new ServiceDefinition();
        serviceDefinition.setNamespace(serviceInterface.getPackage().getName());
        serviceDefinition.setName(serviceInterface.getSimpleName());

        // a type-level @McpTool marks every function a tool, and supplies their shared title and description
        McpTool typeLevelMcpTool = AnnotationUtils.findAnnotation(serviceInterface, McpTool.class);

        // IdlUtil.serviceFunctions decides WHICH functions exist — the same walk ReflectiveServiceDescriptor
        // registers with the ServiceRegistry, so the schema carries exactly the functions the registry serves
        for (Map.Entry<String, Method> function : IdlUtil.serviceFunctions(serviceInterface).entrySet()) {

            // the implementation's override decides generic bindings and annotations, so an inherited
            // CompletableFuture<T> converts with T bound and @McpTool is honored on the override
            Method specificMethod = BridgeMethodResolver.findBridgedMethod(
                    ClassUtils.getMostSpecificMethod(function.getValue(), implementation));

            FunctionDefinition functionDefinition = new FunctionDefinition();
            functionDefinition.setReturnType(conversionContext.convert(
                    ResolvableType.forMethodReturnType(specificMethod, implementation)));

            for (int i = 0; i < specificMethod.getParameterCount(); i++) {

                MethodParameter methodParameter = new MethodParameter(specificMethod, i).withContainingClass(implementation);

                C3Type c3Type = conversionContext.convert(ResolvableType.forMethodParameter(methodParameter));

                // names come from the interface method: AopUtils.selectInvocableMethod returns the
                // interface's method to the invoker, so the interface's parameter names are what
                // named-argument binding resolves. Mcp.test.ts pins the two together.
                functionDefinition.addParameter(IdlUtil.parameterName(new MethodParameter(function.getValue(), i)),
                                                c3Type);
            }

            functionDefinition.setName(function.getKey());

            McpToolC3Decorator mcpTool = createMcpToolDecorator(serviceInterface,
                                                                 function.getValue(),
                                                                 specificMethod,
                                                                 typeLevelMcpTool);
            if (mcpTool != null) {
                functionDefinition.setDecorators(List.of(mcpTool));
            }

            serviceDefinition.addFunction(functionDefinition);
        }

        return serviceDefinition;
    }

    /**
     * Builds the MCP tool decorator for a function, or null when no declaration exposes it as a tool.
     */
    private McpToolC3Decorator createMcpToolDecorator(Class<?> serviceInterface,
                                                      Method interfaceMethod,
                                                      Method specificMethod,
                                                      McpTool typeLevelMcpTool) {
        // findAnnotation walks super methods, so a declaration applies whether it sits on the interface
        // method or only on the implementation's override (e.g. an inherited CRUD method)
        McpTool methodMcpTool = AnnotationUtils.findAnnotation(specificMethod, McpTool.class);
        McpToolInfo mcpToolInfo = AnnotationUtils.findAnnotation(specificMethod, McpToolInfo.class);

        McpToolC3Decorator ret = null;
        if (methodMcpTool != null || typeLevelMcpTool != null) {

            String functionName = interfaceMethod.getName();

            // the declaration nearest the function owns its hints outright, so what that declaration says —
            // including saying nothing — is what is served. A type-level @McpTool sweeps functions in
            // without describing any one of them, so a function it exposes is hinted by its own name.
            McpToolHints hints;
            if (methodMcpTool != null) {
                hints = McpToolHints.of(methodMcpTool);
            } else if (mcpToolInfo != null) {
                hints = McpToolHints.of(mcpToolInfo);
            } else {
                hints = McpToolHints.forFunctionName(functionName);
            }

            String title = firstStated(methodMcpTool != null ? methodMcpTool.title() : "",
                                       mcpToolInfo != null ? mcpToolInfo.title() : "",
                                       typeLevelMcpTool != null ? typeLevelMcpTool.title() : "");
            if (title.isEmpty()) {
                // the service name qualifies a derived title, so the same function name on many services
                // stays distinguishable in a tool listing
                title = IdlUtil.titleCase(serviceInterface.getSimpleName()) + " " + IdlUtil.titleCase(functionName);
            }

            String description = firstStated(methodMcpTool != null ? methodMcpTool.description() : "",
                                             mcpToolInfo != null ? mcpToolInfo.description() : "",
                                             typeLevelMcpTool != null ? typeLevelMcpTool.description() : "");
            if (description.isEmpty()) {
                description = describe(interfaceMethod, specificMethod);
            }

            ret = new McpToolC3Decorator()
                    .setTitle(title)
                    .setDescription(description)
                    .setReadOnlyHint(hints.readOnly())
                    .setDestructiveHint(hints.destructive())
                    .setIdempotentHint(hints.idempotent());
        }
        return ret;
    }

    /**
     * Describes a function no declaration describes: the compile-time extracted Javadoc, then the function
     * name as a sentence, so an LLM caller always has something to choose the tool by.
     */
    private String describe(Method interfaceMethod, Method specificMethod) {
        String ret = javadocDescription(specificMethod, interfaceMethod);
        if (ret == null || ret.isEmpty()) {
            ret = IdlUtil.sentenceCase(interfaceMethod.getName());
        }
        return ret;
    }

    private static String firstStated(String... values) {
        String ret = "";
        for (String value : values) {
            if (!value.isEmpty()) {
                ret = value;
                break;
            }
        }
        return ret;
    }

    /**
     * Looks up the compile-time extracted Javadoc description for a function, walking the most specific
     * declaration first: the implementation's override, the interface's declaration, then the interface's
     * ancestors — so an inherited CRUD function finds the doc written on the generic base.
     */
    private String javadocDescription(Method specificMethod, Method interfaceMethod) {
        String ret = null;
        String functionName = interfaceMethod.getName();
        Deque<Class<?>> queue = new ArrayDeque<>(List.of(specificMethod.getDeclaringClass(),
                                                         interfaceMethod.getDeclaringClass()));
        Set<Class<?>> visited = new HashSet<>();
        while (ret == null && !queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (visited.add(type)) {
                ret = javadocFor(type).get(functionName);
                if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
                    queue.add(type.getSuperclass());
                }
                queue.addAll(Arrays.asList(type.getInterfaces()));
            }
        }
        return ret;
    }

    private Map<String, String> javadocFor(Class<?> type) {
        return javadocCache.computeIfAbsent(type, clazz -> {
            Map<String, String> ret = Map.of();
            try (InputStream in = clazz.getResourceAsStream(
                    "/" + McpToolDocProcessor.DOCS_RESOURCE_DIRECTORY + clazz.getName() + ".properties")) {
                if (in != null) {
                    Properties properties = new Properties();
                    // load(Reader), the InputStream overload assumes ISO-8859-1 and mangles UTF-8 docs
                    properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    Map<String, String> docs = new HashMap<>();
                    properties.forEach((key, value) -> docs.put((String) key, (String) value));
                    ret = docs;
                }
            } catch (IOException e) {
                log.warn("Failed to read the Javadoc description resource for {}", clazz.getName(), e);
            }
            return ret;
        });
    }

}
