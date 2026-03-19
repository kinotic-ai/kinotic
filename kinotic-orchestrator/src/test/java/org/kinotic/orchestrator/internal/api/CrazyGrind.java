

package org.kinotic.orchestrator.internal.api;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class CrazyGrind {

    private final String slogan;

    public CrazyGrind(String slogan) {
        this.slogan = slogan;
    }

    public String getSlogan() {
        return slogan;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        CrazyGrind that = (CrazyGrind) o;

        return new EqualsBuilder()
                .append(slogan, that.slogan)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(slogan)
                .toHashCode();
    }
}
