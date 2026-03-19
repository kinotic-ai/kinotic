package org.kinotic.persistence.internal.api.services.sql;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.persistence.api.model.ParameterHolder;
import org.kinotic.persistence.api.model.QueryParameter;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 5/8/24.
 */
@RequiredArgsConstructor
@Getter
public class ListParameterHolder implements ParameterHolder {

    private final List<QueryParameter> parameters;

    @Override
    public boolean isEmpty() {
        return parameters == null || parameters.isEmpty();
    }
}
