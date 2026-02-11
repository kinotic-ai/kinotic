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
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 *
 * Created by Navid Mitchell on 6/10/20
 */
public class SimpleObject {

    private String firstName;

    private String lastName;

    private int count;

    private long bigCount;

    public SimpleObject() {
    }

    public String getFirstName() {
        return firstName;
    }

    public SimpleObject setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public SimpleObject setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public int getCount() {
        return count;
    }

    public SimpleObject setCount(int count) {
        this.count = count;
        return this;
    }

    public long getBigCount() {
        return bigCount;
    }

    public SimpleObject setBigCount(long bigCount) {
        this.bigCount = bigCount;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("firstName", firstName)
                .append("lastName", lastName)
                .append("count", count)
                .append("bigCount", bigCount)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        SimpleObject that = (SimpleObject) o;

        return new EqualsBuilder()
                .append(count, that.count)
                .append(bigCount, that.bigCount)
                .append(firstName, that.firstName)
                .append(lastName, that.lastName)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(firstName)
                .append(lastName)
                .append(count)
                .append(bigCount)
                .toHashCode();
    }
}
