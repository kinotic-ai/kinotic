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

package org.kinotic.continuum.internal;

import org.apache.commons.lang3.ClassUtils;
import org.kinotic.continuum.api.annotations.ContinuumPackages;
import org.kinotic.continuum.api.annotations.Proxy;
import org.kinotic.continuum.internal.utils.MetaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;


/**
 * {@link BeanFactoryPostProcessor} that registers {@link Proxy} beans with the given {@link org.springframework.context.ApplicationContext}
 *
 * Created by Navid Mitchell on 04/17/19.
 */
@Component
public class ProxyRegistrationBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ProxyRegistrationBeanDefinitionRegistryPostProcessor.class);

    private ApplicationContext applicationContext;

    /**
     * Creates a {@link RpcServiceProxyBeanFactory} for all Interfaces on the classpath that are annotated with {@link Proxy}
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ConfigurableListableBeanFactory listableBeanFactory = null;
        if(registry instanceof ConfigurableListableBeanFactory){
            listableBeanFactory = (ConfigurableListableBeanFactory) registry;
        }

        if(listableBeanFactory == null){
            log.error("BeanDefinitionRegistry is not of type ConfigurableListableBeanFactory. Proxies cannot be created!");
            throw new FatalBeanException("BeanDefinitionRegistry is not of type ConfigurableListableBeanFactory");
        }

        // scan classpath for all Classes annotated with @Proxy
        List<String> packages = ContinuumPackages.get(this.applicationContext);
        packages.add("org.kinotic.continuum.internal"); // core continuum proxies

        Set<MetadataReader> readers = MetaUtil.findClassesWithAnnotation(applicationContext, packages, Proxy.class);

        for(MetadataReader reader: readers){
            String serviceClassName = reader.getClassMetadata().getClassName();
            Class<?> serviceClass;
            try {
                serviceClass = Class.forName(serviceClassName);
            } catch (ClassNotFoundException e) {
                throw new FatalBeanException("Could not load class. Should never happen!",e);
            }

            // We check if there is a bean implementing the Proxy interface.
            // If there is no proxy will be created
            // This allows an interface to be Annotated with both Proxy and Publish, when this is done multiple microservices can share the Interface definitions
            if(listableBeanFactory.getBeanNamesForType(serviceClass).length == 0) {

                BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RpcServiceProxyBeanFactory.class);
                builder.addConstructorArgValue(serviceClass);

                log.debug("Registering bean factory for RPC Proxy: {}", serviceClassName);
                registry.registerBeanDefinition(ClassUtils.getShortClassName(serviceClassName) + "Factory", builder.getBeanDefinition());
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
