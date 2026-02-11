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

package org.kinotic.continuum.internal.core.api.aignite;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryEventFilter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Navid Mitchell on 8/2/17.
 */
public class ScriptCacheEntryFilterFactory<I,T> implements Factory<CacheEntryEventFilter<I, T>> {

    private final String filterSource;
    private final String scriptSource;
    private final Object[] args;
    private ScriptCacheEntryEventFilter<I,T> cacheEntryEventFilter = null;

    public ScriptCacheEntryFilterFactory(String filterSource, Object... args) {
        Validate.notBlank(filterSource);
        this.filterSource = filterSource;
        this.args = args;
        StringBuilder sb = new StringBuilder("def zRet = ");
        int argsUsed = 0;
        List<String> segments = Arrays.stream(filterSource.split(" "))
                                      .map(String::trim)
                                      .filter(StringUtils::isNotEmpty)
                                      .collect(Collectors.toList());

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            switch (segment) {
                case "?":

                    sb.append(" ");
                    sb.append(extractArg(argsUsed, args));
                    sb.append(" ");
                    argsUsed++;
                    break;
                case "and":

                    sb.append("&&");
                    break;
                case "or":

                    sb.append("||");
                    break;
                case "=":

                    sb.append("==");
                    break;
                case "like":

                    sb.append("==~ /");
                    i++;
                    Validate.isTrue(i < segments.size());

                    String likeVal = segments.get(i);
                    if (likeVal.equals("?")) {
                        sb.append(extractArg(argsUsed, args).replace("%", ".*"));
                        argsUsed++;
                    } else {
                        sb.append(likeVal.replace("%", ".*"));
                    }
                    sb.append("/");
                    break;
                case "order":
                case "group":
                    // ignore the next 3 segments, e.g. order by timestamp asc
                    // we may want to support specifying which field to use and which direction.
                    i += 3;
                    break;
                default:
                    sb.append(segment);
                    break;
            }

            sb.append(" ");
        }
        sb.append("\n zRet \n");
        this.scriptSource = sb.toString();
    }

    private String extractArg(int index, Object[] args){
        String ret;
        Validate.isTrue(index < args.length,"Not enough parameters... Expected >= "+index+" got "+args.length+"\n"+
                                          " For Query Filter "+filterSource);
        Object arg = args[index];

        if((arg instanceof String && StringUtils.isNumeric((String)arg))
            || arg instanceof Long || arg instanceof Integer){
            ret = arg + "L"; // make everything a long literal since widening never hurt anyone
        }else if(arg instanceof Double){
            ret = "" + arg +"d";
        }else{
            ret = "" + arg; // coerce to string
        }

        return ret;
    }


    @Override
    public CacheEntryEventFilter<I, T> create() {
        try {
            if(cacheEntryEventFilter == null){
                GroovyShellCompiler shellCompiler = new GroovyShellCompiler(Thread.currentThread()
                                                                                  .getContextClassLoader(),
                                                                            ScriptFilter.class);
                Class<?> clazz = shellCompiler.parse("CacheFilter", new StringReader(scriptSource));
                Object instance = clazz.getDeclaredConstructor().newInstance();
                cacheEntryEventFilter = new ScriptCacheEntryEventFilter<>(scriptSource, (ScriptFilter)instance, args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cacheEntryEventFilter;
    }
}
