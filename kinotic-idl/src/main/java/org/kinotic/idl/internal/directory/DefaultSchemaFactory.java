

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.idl.api.annotations.McpTool;
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

import java.util.List;
import java.util.Map;

import java.lang.reflect.Method;

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
    public NamespaceDefinition createForServices(Map<Class<?>, Class<?>> services) {
        Assert.notNull(services, "services cannot be null");
        // one conversion context for the whole batch, so complex types shared between services convert once
        DefaultConversionContext conversionContext = new DefaultConversionContext(typeConverter, true);

        NamespaceDefinition ret = new NamespaceDefinition();
        for (Map.Entry<Class<?>, Class<?>> service : services.entrySet()) {
            // a service with an unconvertible type is omitted so the rest of the batch still converts;
            // ObjectC3Types are cached only after converting completely, so a failure leaves no partial types
            try {
                ret.addServiceDefinition(createForService(service.getKey(), service.getValue(), conversionContext));
            } catch (Exception e) {
                log.error("Failed to create ServiceDefinition for {}", service.getKey().getName(), e);
            }
        }
        ret.setComplexC3Types(conversionContext.getComplexC3Types());
        return ret;
    }

    private ServiceDefinition createForService(Class<?> contract,
                                               Class<?> implementation,
                                               ConversionContext conversionContext) {
        Assert.notNull(contract, "contract cannot be null");
        Assert.notNull(implementation, "implementation cannot be null");

        ServiceDefinition serviceDefinition = new ServiceDefinition();
        serviceDefinition.setNamespace(contract.getPackage().getName());
        serviceDefinition.setName(contract.getSimpleName());

        // a type-level @McpTool marks every function a tool with these defaults
        McpTool typeLevelMcpTool = AnnotationUtils.findAnnotation(contract, McpTool.class);

        // IdlUtil.serviceFunctions decides WHICH functions exist — the same walk ReflectiveServiceDescriptor
        // registers with the ServiceRegistry, so the schema carries exactly the functions the registry serves
        for (Map.Entry<String, Method> function : IdlUtil.serviceFunctions(contract).entrySet()) {

            // the implementation's override decides parameter names, generic bindings, and annotations —
            // the same method the invocation-side named-argument binding resolves, so the published schema
            // and the runtime binding cannot drift
            Method specificMethod = BridgeMethodResolver.findBridgedMethod(
                    ClassUtils.getMostSpecificMethod(function.getValue(), implementation));

            FunctionDefinition functionDefinition = new FunctionDefinition();
            functionDefinition.setReturnType(conversionContext.convert(
                    ResolvableType.forMethodReturnType(specificMethod, implementation)));

            for (int i = 0; i < specificMethod.getParameterCount(); i++) {

                MethodParameter methodParameter = new MethodParameter(specificMethod, i).withContainingClass(implementation);

                C3Type c3Type = conversionContext.convert(ResolvableType.forMethodParameter(methodParameter));

                functionDefinition.addParameter(IdlUtil.parameterName(methodParameter), c3Type);
            }

            functionDefinition.setName(function.getKey());

            // findAnnotation walks super methods, so @McpTool applies whether declared on the contract
            // method or only on the implementation's override (e.g. an inherited CRUD method); a
            // method-level annotation overrides the type-level defaults for that method
            McpTool mcpTool = AnnotationUtils.findAnnotation(specificMethod, McpTool.class);
            if (mcpTool == null) {
                mcpTool = typeLevelMcpTool;
            }
            if(mcpTool != null){
                // an LLM caller decides which tool to invoke by its description, so an empty description
                // or title falls back to one derived from the function name rather than staying blank
                functionDefinition.setDecorators(List.of(new McpToolC3Decorator()
                        .setTitle(mcpTool.title().isEmpty() ? deriveTitle(function.getKey()) : mcpTool.title())
                        .setDescription(mcpTool.description().isEmpty() ? deriveDescription(function.getKey()) : mcpTool.description())
                        .setReadOnlyHint(mcpTool.readOnlyHint())
                        .setDestructiveHint(mcpTool.destructiveHint())
                        .setIdempotentHint(mcpTool.idempotentHint())));
            }

            serviceDefinition.addFunction(functionDefinition);
        }

        return serviceDefinition;
    }

    // createApplicationIfNotExist -> "Create Application If Not Exist"
    private static String deriveTitle(String functionName) {
        StringBuilder ret = new StringBuilder();
        for (String word : StringUtils.splitByCharacterTypeCamelCase(functionName)) {
            if (!ret.isEmpty()) {
                ret.append(' ');
            }
            ret.append(StringUtils.capitalize(word));
        }
        return ret.toString();
    }

    // createApplicationIfNotExist -> "Create application if not exist"
    private static String deriveDescription(String functionName) {
        StringBuilder ret = new StringBuilder();
        for (String word : StringUtils.splitByCharacterTypeCamelCase(functionName)) {
            if (ret.isEmpty()) {
                ret.append(StringUtils.capitalize(word));
            } else {
                ret.append(' ');
                // an all-caps word is an acronym (CRI, OIDC) and keeps its case
                ret.append(StringUtils.isAllUpperCase(word) ? word : StringUtils.uncapitalize(word));
            }
        }
        return ret.toString();
    }

}
