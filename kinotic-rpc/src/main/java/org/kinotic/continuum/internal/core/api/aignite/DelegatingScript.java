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

import groovy.lang.*;

/**
 * TODO: document my changes, it doesn't function exactly like this anymore
 * {@link Script} that performs method invocations and property access like {@link Closure} does.
 *
 * <p>
 * {@link DelegatingScript} is a convenient basis for loading a custom-defined DSL as a {@link Script}, then execute it.
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
 * {@link DelegatingScript} does this by delegating property access and method invocation to the delegate object.
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
 * Using {@link DelegatingScript} as the base class, the code will run as:
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
public abstract class DelegatingScript extends Script {
    private GroovyObject delegate;

    protected DelegatingScript() {
        super();
    }

    protected DelegatingScript(Binding binding) {
        super(binding);
    }

    /**
     * Sets the delegation target.
     */
    public void setDelegate(GroovyObject delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            return super.invokeMethod(name, args);
        } catch (MissingMethodException mme) {
            return delegate.invokeMethod(name, args);
        }
    }

    // TODO: document this better
    // Cool thing with setup below
    // in a script you can test a global variable for null,  No Hardcoded Strings!!
    // And if it is not there you can then set it and it will then become part of the global scope

    // Basically full getProp in this order
    // delegate -> script_binding -> script_local_vars  (In our case the Delegate is the Context)

    @Override
    public Object getProperty(String property) {
        try {
            return delegate.getProperty(property);
        } catch (MissingPropertyException e) {
            try {
                return super.getProperty(property);
            } catch (Exception e1) {
                return null;
            }
        }
    }

    // Basically full setProp in this order
    // delegate -> script_binding  (In our case the Delegate is the Context)

    @Override
    public void setProperty(String property, Object newValue) {
        try {
            delegate.setProperty(property,newValue);
        } catch (MissingPropertyException e) {
            super.setProperty(property,newValue);
        }
    }

    public Object getDelegate() {
        return delegate;
    }
}
