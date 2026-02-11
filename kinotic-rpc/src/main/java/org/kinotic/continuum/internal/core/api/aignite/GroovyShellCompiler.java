/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.kinotic.continuum.internal.core.api.aignite;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.Reader;

/**
 * @author Julien Viet
 * @author Navid
 */
public class GroovyShellCompiler {

    private final GroovyClassLoader gcl;

    public GroovyShellCompiler(ClassLoader baseLoader, Class<? extends Script> baseScriptClass) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setRecompileGroovySource(true);
        config.setScriptBaseClass(baseScriptClass.getName());
        this.gcl = new GroovyClassLoader(baseLoader, config);
    }

    public GroovyShellCompiler(ClassLoader baseLoader) {
        this(baseLoader,DelegatingScript.class);
    }

    public Class<?> parse(String name, Reader source) {
        Class<?> clazz;
        try {

            GroovyCodeSource gcs = new GroovyCodeSource(source, name, "/groovy/shell");

            clazz = gcl.parseClass(gcs, false);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return clazz;
    }
}
