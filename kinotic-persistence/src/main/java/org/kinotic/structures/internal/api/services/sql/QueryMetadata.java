package org.kinotic.structures.internal.api.services.sql;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;

import java.util.List;

/**
 * Created By Navíd Mitchell 🤪on 2/23/25
 */
@Builder
@Getter
@Setter
public class QueryMetadata {

    @Singular
    private List<String> queryParameterNames;

    private String tenantSelectionParameterName;
    private String queryOptionsParameterName;

    private boolean hasTimeZoneOptionParameter;
    private boolean hasRequestTimeoutOptionParameter;
    private boolean hasPageTimeoutOptionParameter;

    public boolean queryExpectsParameters(){
        return (queryParameterNames != null && !queryParameterNames.isEmpty())
                || tenantSelectionParameterName != null
                || queryOptionsParameterName != null
                || hasTimeZoneOptionParameter
                || hasRequestTimeoutOptionParameter
                || hasPageTimeoutOptionParameter;
    }

}
