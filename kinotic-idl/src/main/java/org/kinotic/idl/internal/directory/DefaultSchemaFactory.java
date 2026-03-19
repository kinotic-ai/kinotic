

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.annotations.Name;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Provides the ability to create {@link C3Type}'s
 *
 *
 * Created by navid on 2019-06-13.
 */
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
    public NamespaceDefinition createForService(Class<?> clazz) {
        DefaultConversionContext conversionContext = new DefaultConversionContext(typeConverter, true);
        return this.createForService(clazz, conversionContext);
    }

    private NamespaceDefinition createForService(Class<?> clazz, ConversionContext conversionContext) {
        Assert.notNull(clazz, "Class cannot be null");
        Assert.notNull(conversionContext, "ConversionContext cannot be null");


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

                functionDefinition.addParameter(getName(methodParameter), c3Type);
            }

            functionDefinition.setName(method.getName());
            serviceDefinition.addFunction(functionDefinition);

        }, ReflectionUtils.USER_DECLARED_METHODS);

        NamespaceDefinition ret = new NamespaceDefinition();
        ret.setComplexC3Types(conversionContext.getComplexC3Types());
        ret.addServiceDefinition(serviceDefinition);

        return ret;
    }

    private String getName(MethodParameter methodParameter){
        String ret;
        Name nameAnnotation = methodParameter.getParameterAnnotation(Name.class);
        if(nameAnnotation != null){
            ret = nameAnnotation.value();
        }else{
            ret = methodParameter.getParameter().getName();
        }
        return ret;
    }

}
