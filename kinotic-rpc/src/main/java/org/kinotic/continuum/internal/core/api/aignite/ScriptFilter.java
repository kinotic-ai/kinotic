/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.kinotic.continuum.internal.core.api.aignite;

import org.kinotic.continuum.core.api.event.StreamData;
import groovy.lang.*;
import org.apache.ignite.binary.BinaryField;
import org.apache.ignite.binary.BinaryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.Map;
import java.util.function.Function;

/**
 * TODO: document my changes it doesn't function exactly like this anymore
 * {@link Script} that performs method invocations and property access like {@link Closure} does.
 *
 * <p>
 * {@link ScriptFilter} is a convenient basis for loading a custom-defined DSL as a {@link Script}, then execute it.
 * The following sample code illustrates how to do it:
 *
 * <pre>
 * class MyDSL {
 *     public void foo(int x, int y, Closure z) { ... }
 *     public void setBar(String a) { ... }
 * }
 *
 * CompilerConfiguration cc = new CompilerConfiguration();
 * cc.setScriptBaseClass(DelegatingScript.class.getName());
 * GroovyShell sh = new GroovyShell(cl,new Binding(),cc);
 * DelegatingScript script = (DelegatingScript)sh.parse(new File("my.dsl"))
 * script.setDelegate(new MyDSL());
 * script.run();
 * </pre>
 *
 * <p>
 *
 * <pre>
 * foo(1,2) {
 *     ....
 * }
 * bar = ...;
 * </pre>
 *
 * <p>
 * {@link ScriptFilter} does this by delegating property access and method invocation to the delegate object.
 *
 * <p>
 * More formally speaking, given the following script:
 *
 * <pre>
 * a = 1;
 * b(2);
 * </pre>
 *
 * <p>
 * Using {@link ScriptFilter} as the base class, the code will run as:
 *
 * <pre>
 * delegate.a = 1;
 * delegate.b(2);
 * </pre>
 *
 * ... whereas in plain {@link Script}, this will be run as:
 *
 * <pre>
 * binding.setProperty("a",1);
 * ((Closure)binding.getProperty("b")).call(2);
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ScriptFilter extends Script {

    private static final Logger log = LoggerFactory.getLogger(ScriptFilter.class);

    private static final Map<String, Function<StreamData<?,?>,Object>> propertyAccessorCache = new ConcurrentReferenceHashMap<>(256);

    private StreamData<?,?> delegate;

    protected ScriptFilter() {
        super();
    }

    protected ScriptFilter(Binding binding) {
        super(binding);
    }

    /**
     * Sets the delegation target.
     */
    public void setDelegate(StreamData<?,?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        Object ret = null;

        return ret;
    }

    // TODO: document this better
    // Cool thing with setup below
    // in a script you can test a global variable for null,  No Hardcoded Strings!!
    // And if it is not there you can then set it and it will then become part of the global scope

    // Basically full getProp in this order
    // delegate -> script_binding -> script_local_vars  (In our case the Delegate is the Context)

    @Override
    public Object getProperty(String property) {
        Object ret = null;

        // We handle known and most accessed properties first to keep the loop fast
        if(property.equals("args")){

            ret = getBinding().getProperty("args");

        }else if(propertyAccessorCache.containsKey(property)){
            // We check the cache however it can be cleaned automatically
            ret = propertyAccessorCache.get(property).apply(delegate);

        }else if(property.equals("operation")){

            ret = delegate.streamOperation();

        }else if(property.equals("identifier")){

            ret = delegate.getId();

        }else{

            Object value = delegate.value();
            if(value instanceof BinaryObject){
                BinaryObject bo = (BinaryObject) value;
                if(bo.hasField(property)) {
                    BinaryField binaryField = bo.type().field(property);
                    propertyAccessorCache.put(property, new BinaryFieldAccessor(binaryField));
                    ret = binaryField.value(bo);
                }else{
                    // How to best handle
                    log.debug("Property "+property+" not found for binary object "+delegate);
                }
            }else if(value instanceof GroovyObject){
                GroovyObject go = (GroovyObject)value;
                MetaProperty metaProperty = go.getMetaClass().getMetaProperty(property);
                if(metaProperty != null){
                    ret = metaProperty.getProperty(go);
                    propertyAccessorCache.put(property, new GroovyPropertyAccessor(metaProperty));
                }else{
                    // How to best handle
                    log.debug("Property "+property+" not found for groovy object "+delegate);
                }
            }
        }
        return ret;
    }

    // Basically full setProp in this order
    // delegate -> script_binding  (In our case the Delegate is the Context)

    @Override
    public void setProperty(String property, Object newValue) {
        Object ret = null;


    }

    public StreamData<?,?> getDelegate() {
        return delegate;
    }

    static class GroovyPropertyAccessor implements Function<StreamData<?,?>,Object>{
        private final MetaProperty metaProperty;

        public GroovyPropertyAccessor(MetaProperty metaProperty) {
            this.metaProperty = metaProperty;
        }

        @Override
        public Object apply(StreamData streamValuez) {
            Object ret = null;
            Object value = streamValuez.value();
            if(value instanceof GroovyObject){
                ret = metaProperty.getProperty(value);
            }
            return ret;
        }
    }

    static class BinaryFieldAccessor implements Function<StreamData<?,?>,Object>{
        private final BinaryField field;

        public BinaryFieldAccessor(BinaryField field) {
            this.field = field;
        }

        @Override
        public Object apply(StreamData streamValuez) {
            Object ret = null;
            Object value = streamValuez.value();
            if(value instanceof BinaryObject){
               ret = field.value((BinaryObject)value);
            }
            return ret;
        }
    }

}
