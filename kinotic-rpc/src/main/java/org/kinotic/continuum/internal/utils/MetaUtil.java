/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.utils;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.kinotic.continuum.api.annotations.Scope;
import org.kinotic.continuum.api.annotations.Version;
import org.springframework.context.ApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Navid Mitchell on 7/22/17.
 */
public class MetaUtil {

    /**
     * Scans the given packages for classes with the given Annotation
     *
     * @param applicationContext the current Spring {@link ApplicationContext}
     * @param packages to scan
     * @param annotationClass the Annotation class that should be searched for
     * @return a Set of {@link MetadataReader}'s for the classes that have the desired annotation
     */
    public static Set<MetadataReader> findClassesWithAnnotation(ApplicationContext applicationContext,
                                                                List<String> packages,
                                                                Class<? extends Annotation> annotationClass){
        ClassPathScanningMetadataReaderProvider scanner
                = new ClassPathScanningMetadataReaderProvider(applicationContext.getEnvironment());
        scanner.setResourceLoader(applicationContext);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotationClass));

        Set<MetadataReader> readers = new HashSet<>();

        for(String pack : packages){
            readers.addAll(scanner.findCandidateComponents(pack));
        }

        return readers;
    }

    /**
     * Scans the given packages for classes that are assignable to the given type
     * @param applicationContext the current Spring {@link ApplicationContext}
     * @param packages to scan
     * @param targetType that classes found must be assignable to
     * @return a Set of {@link MetadataReader}'s for the classes that are found
     */
    public static Set<MetadataReader> findClassesAssignableToType(ApplicationContext applicationContext,
                                                                  List<String> packages,
                                                                  Class<?> targetType){

        Validate.notNull(targetType, "targetType must not be null");

        ClassPathScanningMetadataReaderProvider scanner
                = new ClassPathScanningMetadataReaderProvider(applicationContext.getEnvironment());
        scanner.setResourceLoader(applicationContext);
        scanner.addIncludeFilter(new AssignableTypeFilter(targetType));

        Set<MetadataReader> readers = new HashSet<>();

        for(String pack : packages){
            readers.addAll(scanner.findCandidateComponents(pack));
        }

        return readers;
    }


    /**
     * Tries to get a valid {@link Scope} from the instance provided
     * NOTE: If more than one {@link Scope} method or field are provided the first that is found will be returned
     *       methods are searched then fields.
     * @param instance to search for a {@link Scope}
     * @return the value of the {@link Scope} if found or null
     */
    public static String getScopeIfAvailable(Object instance)throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
        return getScopeIfAvailable(instance, instance.getClass());
    }

    /**
     * Tries to get a valid {@link Scope} from the instance provided for the class given
     * NOTE: If more than one {@link Scope} method or field are provided the first that is found will be returned
     *       methods are searched then fields.
     * @param instance to search for a {@link Scope}
     * @param clazz to use when introspecting the object for {@link Scope}'s
     * @return the value of the {@link Scope} if found or null
     */
    public static String getScopeIfAvailable(Object instance, Class<?> clazz)throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
        Validate.isInstanceOf(clazz, instance, "The instance provided must be an instanceOf the class provided");

        String scope = null;
        // First check methods
        for(Method method : clazz.getMethods()){
            if(scope == null) {
                scope = MetaUtil.getScopeIfAvailable(method, instance);
            }else{
                break;
            }
        }
        // now check fields
        if(scope == null){
            Field[] fields = FieldUtils.getFieldsWithAnnotation(instance.getClass(), Scope.class);
            if(fields.length > 0) {
                scope = (String) FieldUtils.readField(fields[0], instance, true);
            }
        }
        return scope;
    }

    /**
     * Tries to get a valid {@link Scope} from the info provided
     * @param method to check for the annotation
     * @param instance object to invoke for identifier value
     * @return the identifier or null if not present
     */
    public static String getScopeIfAvailable(Method method, Object instance)throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        String ret = null;
        Scope scope = AnnotationUtils.findAnnotation(method, Scope.class);

        if(scope != null){
            Assert.isAssignable(String.class, method.getReturnType(),"@Scope must only be used on String values or return types");
            ret = (String)method.invoke(instance);
        }
        return ret;
    }

    /**
     * Returns the index of the method parameter with a {@link Scope} annotation or returns null
     * @param method to look for {@link Scope} annotations
     * @return the index of parameter with the annotation or null if none
     * @throws IllegalArgumentException if more than one parameter contains a {@link Scope} annotation or the annotation is applied to an invalid type
     */
    public static Integer findParameterIndexWithScopeAnnotation(Method method){
        Method bridgedMethod =  BridgeMethodResolver.findBridgedMethod(method);
        int count = bridgedMethod.getParameterCount();
        Integer ret = null;
        for (int i = 0; i < count; i++) {
            MethodParameter parameter = new MethodParameter(bridgedMethod, i);
            if(parameter.hasParameterAnnotation(Scope.class)){
                Validate.isAssignableFrom(String.class, parameter.getParameterType(), "@Scope must only be used on String values");
                Validate.isTrue(ret == null, "More than one argument was annotated with @Scope. For Method " + method);
                ret = i;
            }
        }
        return ret;
    }

    /**
     * Returns the interface or interfaces that declares the given annotation
     * @param clazz to check for the annotation
     * @param annotation to look for
     * @return a list of interfaces that declare the annotation
     */
    public static List<Class<?>> getInterfaceDeclaringAnnotation(Class<?> clazz, Class<? extends Annotation> annotation){
        ArrayList<Class<?>> ret = new ArrayList<>();

        for(Class<?> interClass: clazz.getInterfaces()){
            if(interClass.isAnnotationPresent(annotation)){
                ret.add(interClass);
            }
        }
        // If there is a superclass we need its interfaces as well
        if(clazz.getSuperclass() != null){
            ret.addAll(getInterfaceDeclaringAnnotation(clazz.getSuperclass(), annotation));
        }
        return ret;
    }

    /**
     * Gets the version for the class by searching for a {@link Version} annotation.
     * If the class contains the annotation that version is returned otherwise it returns the version from the package-info.java file annotation if found.
     * @param clazz to search for a version
     * @return the version or null if not found
     */
    public static String getVersion(Class<?> clazz){
        String ret = null;
        Version version = AnnotationUtils.findAnnotation(clazz, Version.class);
        if(version != null){
            ret = version.value();
        }else{
            Package pkg = clazz.getPackage();
            if(pkg != null){
                version = AnnotationUtils.findAnnotation(pkg, Version.class);
                if(version != null){
                    ret = version.value();
                }
            }
        }
        return ret;
    }

}
