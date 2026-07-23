

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.utils.IdlUtil;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

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
    public NamespaceDefinition createForServices(Collection<Class<?>> serviceInterfaces) {
        Assert.notNull(serviceInterfaces, "serviceInterfaces cannot be null");
        // one conversion context for the whole batch, so complex types shared between services convert once
        DefaultConversionContext conversionContext = new DefaultConversionContext(typeConverter, true);

        NamespaceDefinition ret = new NamespaceDefinition();
        for (Class<?> clazz : new LinkedHashSet<>(serviceInterfaces)) {
            // a service with an unconvertible type is omitted so the rest of the batch still converts;
            // ObjectC3Types are cached only after converting completely, so a failure leaves no partial types
            try {
                ret.addServiceDefinition(createForService(clazz, conversionContext));
            } catch (Exception e) {
                log.error("Failed to create ServiceDefinition for {}", clazz.getName(), e);
            }
        }
        ret.setComplexC3Types(conversionContext.getComplexC3Types());
        return ret;
    }

    private ServiceDefinition createForService(Class<?> clazz, ConversionContext conversionContext) {
        Assert.notNull(clazz, "Class cannot be null");

        ServiceDefinition serviceDefinition = new ServiceDefinition();
        serviceDefinition.setNamespace(clazz.getPackage().getName());
        serviceDefinition.setName(clazz.getSimpleName());

        ReflectionUtils.doWithMethods(clazz, method -> {
            // TODO: make this work properly when an interface defines generics that the implementor will define in implementation, This would require an interface class and a target class above to work correctly

            FunctionDefinition functionDefinition = new FunctionDefinition();
            functionDefinition.setReturnType(conversionContext.convert(ResolvableType.forMethodReturnType(method)));

            for (int i = 0; i < method.getParameterCount(); i++) {

                MethodParameter methodParameter = new MethodParameter(method, i);

                C3Type c3Type = conversionContext.convert(ResolvableType.forMethodParameter(methodParameter));

                functionDefinition.addParameter(IdlUtil.parameterName(methodParameter), c3Type);
            }

            functionDefinition.setName(method.getName());

            McpTool mcpTool = method.getAnnotation(McpTool.class);
            if(mcpTool != null){
                functionDefinition.setDecorators(List.of(new McpToolC3Decorator()
                        .setName(mcpTool.name().isEmpty() ? null : mcpTool.name())
                        .setDescription(mcpTool.description())
                        .setReadOnlyHint(mcpTool.readOnlyHint())
                        .setDestructiveHint(mcpTool.destructiveHint())
                        .setIdempotentHint(mcpTool.idempotentHint())));
            }

            serviceDefinition.addFunction(functionDefinition);

        }, ReflectionUtils.USER_DECLARED_METHODS);

        return serviceDefinition;
    }

}
