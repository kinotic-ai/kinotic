

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.GenericTypeConverter;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

/**
 * Converts all generic POJO's
 * <p>
 * Created by navid on 2019-06-14.
 */
public class PojoTypeConverter implements GenericTypeConverter {

    @Override
    public boolean supports(ResolvableType resolvableType) {
        Class<?> rawClass = resolvableType.getRawClass();

        return rawClass != null
                && !rawClass.getPackage().getName().startsWith("java")
                && !rawClass.getPackage().getName().startsWith("javax")
                && !rawClass.getPackage().getName().startsWith("jdk")
                && !rawClass.getPackage().getName().startsWith("sun")
                && !rawClass.getPackage().getName().startsWith("org.apache.groovy")
                && Object.class.isAssignableFrom(rawClass);
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        Class<?> rawClass = resolvableType.getRawClass();
        Assert.notNull(rawClass, "Raw class could not be found for ResolvableType");

        ObjectC3Type ret = new ObjectC3Type();
        ret.setNamespace(rawClass.getPackage().getName());
        ret.setName(monomorphicName(resolvableType, rawClass));

        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(rawClass);

        for(PropertyDescriptor descriptor: descriptors){

            if(!ignorePropertyDescriptor(descriptor)) {

                // resolving against the owner type binds type variables the property declares,
                // so List<T> getContent() on a Page<Project> converts with T bound to Project
                ResolvableType returnTypeResolvableType =
                        ResolvableType.forMethodParameter(new MethodParameter(descriptor.getReadMethod(), -1),
                                                          resolvableType);

                C3Type fieldC3Type = conversionContext.convert(returnTypeResolvableType);

                ret.addProperty(descriptor.getName(), fieldC3Type);
            }
        }
        return ret;
    }

    /**
     * The C3 name for the given instantiation. C3 has no generics, so each instantiation of a generic class
     * publishes as its own concrete type, named by prefixing the resolved type arguments' simple names onto
     * the raw class's simple name: {@code Page<Organization>} is named "OrganizationPage" and
     * {@code CursorPage<Person>} "PersonCursorPage", with nested instantiations named recursively. A
     * non-generic class keeps its simple name.
     * @param resolvableType the instantiation being converted
     * @param rawClass the raw class of {@code resolvableType}
     * @return the name for the {@link ObjectC3Type}
     * @throws IllegalStateException if a type argument does not resolve to a class, since a signature with an
     *         open type variable cannot be described to a wire consumer
     */
    private String monomorphicName(ResolvableType resolvableType, Class<?> rawClass) {
        StringBuilder name = new StringBuilder();
        for (ResolvableType typeArgument : resolvableType.getGenerics()) {
            Class<?> argumentClass = typeArgument.resolve();
            if (argumentClass == null) {
                throw new IllegalStateException("Cannot convert " + resolvableType + ": type argument '"
                        + typeArgument.getType() + "' of " + rawClass.getName()
                        + " does not resolve to a class, so it cannot be described to a wire consumer");
            }
            name.append(monomorphicName(typeArgument, argumentClass));
        }
        // an array class's simple name is bracketed ("Foo[]"), which cannot appear in a type name
        name.append(rawClass.getSimpleName().replace("[]", "Array"));
        return name.toString();
    }

    private boolean ignorePropertyDescriptor(PropertyDescriptor descriptor){
        boolean ret = descriptor.getReadMethod() == null
                || isInternalObjectMethod(descriptor.getReadMethod());
        return ret;
    }

    private boolean isInternalObjectMethod(Method method){
        boolean ret = false;
        Class<?> declaringClass = method.getDeclaringClass();
        if(declaringClass.isAssignableFrom(Object.class)
           || declaringClass.isAssignableFrom(GroovyObject.class)
           || declaringClass.isAssignableFrom(MetaClass.class)){
            ret = true;
        }
        return ret;
    }

}
