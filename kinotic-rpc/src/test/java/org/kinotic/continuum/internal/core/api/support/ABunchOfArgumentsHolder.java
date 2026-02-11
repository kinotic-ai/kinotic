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

package org.kinotic.continuum.internal.core.api.support;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

/**
 *
 * Created by Navid Mitchell on 6/27/20
 */
public class ABunchOfArgumentsHolder {

    private int intValue;
    private long longValue;
    private String stringValue;
    private boolean boolValue;
    private SimpleObject simpleObject;
    private List<String> listOfStrings;

    public ABunchOfArgumentsHolder() {
    }

    public ABunchOfArgumentsHolder(int intValue,
                                   long longValue,
                                   String stringValue,
                                   boolean boolValue,
                                   SimpleObject simpleObject,
                                   List<String> listOfStrings) {
        this.intValue = intValue;
        this.longValue = longValue;
        this.stringValue = stringValue;
        this.boolValue = boolValue;
        this.simpleObject = simpleObject;
        this.listOfStrings = listOfStrings;
    }

    public int getIntValue() {
        return intValue;
    }

    public ABunchOfArgumentsHolder setIntValue(int intValue) {
        this.intValue = intValue;
        return this;
    }

    public long getLongValue() {
        return longValue;
    }

    public ABunchOfArgumentsHolder setLongValue(long longValue) {
        this.longValue = longValue;
        return this;
    }

    public String getStringValue() {
        return stringValue;
    }

    public ABunchOfArgumentsHolder setStringValue(String stringValue) {
        this.stringValue = stringValue;
        return this;
    }

    public boolean isBoolValue() {
        return boolValue;
    }

    public ABunchOfArgumentsHolder setBoolValue(boolean boolValue) {
        this.boolValue = boolValue;
        return this;
    }

    public SimpleObject getSimpleObject() {
        return simpleObject;
    }

    public ABunchOfArgumentsHolder setSimpleObject(SimpleObject simpleObject) {
        this.simpleObject = simpleObject;
        return this;
    }

    public List<String> getListOfStrings() {
        return listOfStrings;
    }

    public ABunchOfArgumentsHolder setListOfStrings(List<String> listOfStrings) {
        this.listOfStrings = listOfStrings;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        ABunchOfArgumentsHolder that = (ABunchOfArgumentsHolder) o;

        return new EqualsBuilder()
                .append(intValue, that.intValue)
                .append(longValue, that.longValue)
                .append(boolValue, that.boolValue)
                .append(stringValue, that.stringValue)
                .append(simpleObject, that.simpleObject)
                .append(listOfStrings, that.listOfStrings)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(intValue)
                .append(longValue)
                .append(stringValue)
                .append(boolValue)
                .append(simpleObject)
                .append(listOfStrings)
                .toHashCode();
    }
}
